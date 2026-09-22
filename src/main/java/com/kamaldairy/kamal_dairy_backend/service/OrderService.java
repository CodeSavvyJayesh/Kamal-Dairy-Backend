package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.DeliveryAddress;
import com.kamaldairy.kamal_dairy_backend.dto.PlaceOrderRequest;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.PaymentVerificationException;
import com.kamaldairy.kamal_dairy_backend.exception.OutOfStockException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.exception.StockRefundedException;
import com.kamaldairy.kamal_dairy_backend.model.*;
import com.kamaldairy.kamal_dairy_backend.repository.CartRepository;
import com.kamaldairy.kamal_dairy_backend.repository.OrderRepository;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import com.kamaldairy.kamal_dairy_backend.util.Addresses;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;
    private final WalletService walletService;
    private final StockService stockService;
    private final EmailService emailService;
    private final DeliveryCalendar calendar;

    public OrderService(
            CartRepository cartRepository,
            OrderRepository orderRepository,
            ProductRepository productRepository,
            PaymentService paymentService,
            WalletService walletService,
            StockService stockService,
            EmailService emailService,
            DeliveryCalendar calendar
    ) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentService = paymentService;
        this.walletService = walletService;
        this.stockService = stockService;
        this.emailService = emailService;
        this.calendar = calendar;
    }

    /**
     * Places an order ONLY against a payment we can prove is genuine.
     *
     * Seven gates, in order:
     *   0. a valid delivery address - checked first, so a bad address never
     *      burns the payment and the customer can simply retry
     *   1. we have a record of this Razorpay order
     *   2. that record belongs to the caller
     *   3. it was created for a cart order, not a wallet top-up
     *   4. the HMAC signature checks out against our Razorpay secret
     *   5. the payment has not already been used for another order
     *   6. the cart total still equals the amount that was actually charged
     *
     * Then stock is taken under a row lock. If something sold out while the
     * customer was paying, the payment is kept as used, its full amount goes
     * to their wallet, and StockRefundedException is returned - committed,
     * not rolled back, so the refund is never lost.
     */
    @Transactional(noRollbackFor = StockRefundedException.class)
    public Order placeOrder(String userEmail, PlaceOrderRequest request) {

        if (request == null) {
            throw new PaymentVerificationException("Payment details are required.");
        }

        // 0
        DeliveryAddress address = Addresses.require(request.getAddress());

        // 1 + 2
        PaymentOrder paymentOrder = paymentService.requireOwnPaymentOrder(
                userEmail, request.getRazorpayOrderId());

        // 3 - a top-up payment must never double as a free cart order
        if (!paymentOrder.isCartOrder()) {
            throw new PaymentVerificationException(
                    "This payment was for a wallet top-up, not an order.");
        }

        // 4
        paymentService.verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());

        // 5 - atomic, so a replay loses the race instead of duplicating an order
        if (!paymentService.consume(paymentOrder.getId(), request.getRazorpayPaymentId())) {
            throw new PaymentVerificationException(
                    "This payment has already been used for an order.");
        }

        List<CartItem> cartItems = requireCart(userEmail);
        Map<Integer, Product> locked = stockService.lock(cartItems); // before anything reads the products
        PricedOrder priced = price(userEmail, cartItems);

        // 6 - the customer must not be able to swell the cart after paying
        if (priced.totalPaise() != paymentOrder.getAmountPaise()) {
            log.warn("Amount mismatch for {}: charged {} paise but cart is now {} paise",
                    userEmail, paymentOrder.getAmountPaise(), priced.totalPaise());
            throw new PaymentVerificationException(
                    "Your cart changed after payment was started. Nothing has been ordered - "
                    + "please contact support so we can refund or complete this payment.");
        }

        List<String> shortages = stockService.shortages(cartItems, locked);
        if (!shortages.isEmpty()) {
            walletService.credit(userEmail, paymentOrder.getAmountPaise(), WalletTxnSource.REFUND,
                    "PAY-" + request.getRazorpayPaymentId(), "Refund - sold out during payment");
            log.warn("Stock ran out for {} during payment {} - {} credited to wallet",
                    userEmail, request.getRazorpayPaymentId(), paymentOrder.getAmountPaise());
            throw new StockRefundedException(String.join(" ", shortages)
                    + " It sold out while you were paying, so " + Money.label(paymentOrder.getAmountPaise())
                    + " has been added to your Kamal Wallet. Nothing else was charged.");
        }
        stockService.take(cartItems, locked);

        Order order = priced.order();
        order.setPaymentMethod(Order.PAY_RAZORPAY);
        order.deliverTo(address);
        order.setRazorpayOrderId(request.getRazorpayOrderId());
        order.setRazorpayPaymentId(request.getRazorpayPaymentId());

        Order saved = orderRepository.save(order);
        cartRepository.deleteAll(cartItems);
        emailService.sendOrderPlaced(saved);

        log.info("Order {} placed for {} ({} paise, payment {})",
                saved.getId(), userEmail, priced.totalPaise(), request.getRazorpayPaymentId());

        return saved;
    }

    /**
     * Pays for the cart from the prepaid wallet.
     *
     * One transaction: the order row, the wallet debit and the cart clear all
     * commit together or not at all. If the balance is short the debit throws
     * 402 and the order insert rolls back with it.
     */
    @Transactional
    public Order placeOrderWithWallet(String userEmail, DeliveryAddress deliverTo) {

        DeliveryAddress address = Addresses.require(deliverTo);

        List<CartItem> cartItems = requireCart(userEmail);
        Map<Integer, Product> locked = stockService.lock(cartItems); // before anything reads the products
        PricedOrder priced = price(userEmail, cartItems);

        // Nothing has been written yet, so a shortage simply rolls back: no charge.
        List<String> shortages = stockService.shortages(cartItems, locked);
        if (!shortages.isEmpty()) {
            throw new OutOfStockException(String.join(" ", shortages)
                    + " Update your cart and try again - nothing was charged.");
        }

        Order order = priced.order();
        order.setPaymentMethod(Order.PAY_WALLET);
        order.deliverTo(address);

        Order saved = orderRepository.save(order);

        walletService.debit(userEmail, priced.totalPaise(), WalletTxnSource.ORDER,
                "ORDER-" + saved.getId(), "Order #" + saved.getId());

        stockService.take(cartItems, locked);
        cartRepository.deleteAll(cartItems);
        emailService.sendOrderPlaced(saved);

        log.info("Order {} placed for {} from wallet ({} paise)", saved.getId(), userEmail, priced.totalPaise());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> getUserOrders(String userEmail) {
        List<Order> orders = orderRepository.findByUserEmail(userEmail);
        orders.forEach(o -> o.getItems().size()); // load items while the session is open
        return orders;
    }


    // ---------------------------------------------------------------- helpers

    private record PricedOrder(Order order, long totalPaise) {}

    private List<CartItem> requireCart(String userEmail) {
        List<CartItem> cartItems = cartRepository.findByUserEmail(userEmail);

        if (cartItems.isEmpty()) {
            throw new ApiException("Your cart is empty.", HttpStatus.BAD_REQUEST);
        }
        return cartItems;
    }

    /** Builds the order from live product prices - never from prices stored on the cart row. */
    private PricedOrder price(String userEmail, List<CartItem> cartItems) {

        Order order = new Order();
        order.setUserEmail(userEmail);
        order.setStatus(OrderStatus.PLACED);
        order.setCreatedAt(calendar.now()); // shop time (IST), same clock as every later status

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {

            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product in your cart (id " + cartItem.getProductId() + ")"));

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(cartItem.getQuantity());
            item.setPrice(product.getPrice());
            item.setOrder(order);

            orderItems.add(item);

            total = total.add(
                    BigDecimal.valueOf(product.getPrice())
                            .multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        long totalPaise = total.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        order.setItems(orderItems);
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP).doubleValue());

        return new PricedOrder(order, totalPaise);
    }
}
