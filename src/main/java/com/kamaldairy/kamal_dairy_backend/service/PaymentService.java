package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.PaymentOrderResponse;
import com.kamaldairy.kamal_dairy_backend.exception.PaymentVerificationException;
import com.kamaldairy.kamal_dairy_backend.model.PaymentOrder;
import com.kamaldairy.kamal_dairy_backend.repository.PaymentOrderRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final RazorpayClient razorpayClient;
    private final PaymentOrderRepository paymentOrderRepository;
    private final CartService cartService;

    private final String razorpayKey;
    private final String razorpaySecret;

    public PaymentService(
            RazorpayClient razorpayClient,
            PaymentOrderRepository paymentOrderRepository,
            CartService cartService,
            @Value("${razorpay.key}") String razorpayKey,
            @Value("${razorpay.secret}") String razorpaySecret
    ) {
        this.razorpayClient = razorpayClient;
        this.paymentOrderRepository = paymentOrderRepository;
        this.cartService = cartService;
        this.razorpayKey = razorpayKey;
        this.razorpaySecret = razorpaySecret;
    }

    /**
     * Creates a Razorpay order for the CALLER'S OWN CART.
     *
     * There is no amount parameter any more. The old endpoint took
     * ?amount=... from the query string and was permitAll, so anybody could
     * mint an order for any figure they liked.
     */
    @Transactional
    public PaymentOrderResponse createOrderForCart(String userEmail) throws Exception {

        long amountPaise = cartService.calculateTotalPaise(userEmail);

        JSONObject options = new JSONObject();
        options.put("amount", amountPaise);
        options.put("currency", "INR");
        options.put("receipt", "rcpt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        options.put("payment_capture", 1);

        com.razorpay.Order razorpayOrder = razorpayClient.orders.create(options);

        String razorpayOrderId = razorpayOrder.get("id");

        // Remember our own version of the truth: who it is for, and what it costs.
        paymentOrderRepository.save(
                new PaymentOrder(razorpayOrderId, userEmail, amountPaise));

        log.info("Created Razorpay order {} for {} ({} paise)", razorpayOrderId, userEmail, amountPaise);

        return new PaymentOrderResponse(razorpayOrderId, amountPaise, "INR", razorpayKey);
    }

    /**
     * Verifies the HMAC signature Razorpay Checkout returns to the browser.
     * Without this check, "payment succeeded" is just a claim made by the client.
     */
    public void verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {

        if (isBlank(razorpayOrderId) || isBlank(razorpayPaymentId) || isBlank(razorpaySignature)) {
            throw new PaymentVerificationException("Incomplete payment details.");
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("razorpay_order_id", razorpayOrderId);
            payload.put("razorpay_payment_id", razorpayPaymentId);
            payload.put("razorpay_signature", razorpaySignature);

            if (!Utils.verifyPaymentSignature(payload, razorpaySecret)) {
                log.warn("Payment signature verification FAILED for order {}", razorpayOrderId);
                throw new PaymentVerificationException("Payment could not be verified.");
            }

        } catch (PaymentVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Payment signature verification error for order {}", razorpayOrderId, e);
            throw new PaymentVerificationException("Payment could not be verified.");
        }
    }

    /**
     * Loads our record of the Razorpay order and confirms it belongs to the caller.
     */
    @Transactional(readOnly = true)
    public PaymentOrder requireOwnPaymentOrder(String userEmail, String razorpayOrderId) {

        PaymentOrder paymentOrder = paymentOrderRepository
                .findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new PaymentVerificationException("Unknown payment reference."));

        if (!paymentOrder.getUserEmail().equalsIgnoreCase(userEmail)) {
            log.warn("User {} tried to use payment order {} belonging to {}",
                    userEmail, razorpayOrderId, paymentOrder.getUserEmail());
            throw new PaymentVerificationException("Unknown payment reference.");
        }

        return paymentOrder;
    }

    /**
     * Atomically flips CREATED -> PAID. Returns false if some other request
     * already consumed this payment, which is what stops the same successful
     * payment from being replayed into several free orders.
     */
    @Transactional
    public boolean consume(Long paymentOrderId, String razorpayPaymentId) {
        return paymentOrderRepository.markPaid(paymentOrderId, razorpayPaymentId,
                java.time.LocalDateTime.now()) == 1;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
