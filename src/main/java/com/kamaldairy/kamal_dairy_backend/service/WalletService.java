package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.PaymentOrderResponse;
import com.kamaldairy.kamal_dairy_backend.dto.PlaceOrderRequest;
import com.kamaldairy.kamal_dairy_backend.dto.WalletTransactionResponse;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.InsufficientBalanceException;
import com.kamaldairy.kamal_dairy_backend.exception.PaymentVerificationException;
import com.kamaldairy.kamal_dairy_backend.model.PaymentOrder;
import com.kamaldairy.kamal_dairy_backend.model.WalletTransaction;
import com.kamaldairy.kamal_dairy_backend.model.WalletTxnSource;
import com.kamaldairy.kamal_dairy_backend.model.WalletTxnType;
import com.kamaldairy.kamal_dairy_backend.repository.WalletRepository;
import com.kamaldairy.kamal_dairy_backend.repository.WalletTransactionRepository;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Prepaid wallet.
 *
 * Invariants:
 *  - the balance can never go below zero (the debit is a conditional UPDATE)
 *  - every change writes exactly one ledger row, in the same transaction
 *  - money only enters via a verified Razorpay payment or an admin refund
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    public static final long MIN_TOPUP_PAISE = 50_00;        // Rs 50
    public static final long MAX_TOPUP_PAISE = 10_000_00;    // Rs 10,000 per top-up
    public static final long MAX_BALANCE_PAISE = 50_000_00;  // Rs 50,000 held at once

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final DeliveryCalendar calendar;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository transactionRepository,
                         PaymentService paymentService,
                         EmailService emailService,
                         DeliveryCalendar calendar) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.paymentService = paymentService;
        this.emailService = emailService;
        this.calendar = calendar;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public long balancePaise(String email) {
        return walletRepository.findBalance(email).orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> recent(String email) {
        return transactionRepository.findTop8ByUserEmailOrderByCreatedAtDescIdDesc(email)
                .stream().map(WalletService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> transactions(String email, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        return transactionRepository
                .findByUserEmailOrderByCreatedAtDescIdDesc(email, PageRequest.of(Math.max(page, 0), safeSize))
                .map(WalletService::toResponse);
    }

    // -------------------------------------------------------------- mutations

    @Transactional
    public WalletTransaction credit(String email, long amountPaise, WalletTxnSource source,
                                    String referenceId, String description) {
        requirePositive(amountPaise);

        if (!walletRepository.existsByUserEmail(email)) {
            walletRepository.insertIfAbsent(email, calendar.now());
        }

        if (walletRepository.credit(email, amountPaise, calendar.now()) != 1) {
            throw new IllegalStateException("Wallet for " + email + " could not be credited");
        }

        return record(email, WalletTxnType.CREDIT, source, amountPaise, referenceId, description);
    }

    /** Debit or throw 402 with a readable message. */
    @Transactional
    public WalletTransaction debit(String email, long amountPaise, WalletTxnSource source,
                                   String referenceId, String description) {
        return tryDebit(email, amountPaise, source, referenceId, description)
                .orElseThrow(() -> new InsufficientBalanceException(amountPaise, balancePaise(email)));
    }

    /** Debit if the balance covers it; otherwise change nothing and return empty. */
    @Transactional
    public Optional<WalletTransaction> tryDebit(String email, long amountPaise, WalletTxnSource source,
                                                String referenceId, String description) {
        requirePositive(amountPaise);

        if (walletRepository.debitIfSufficient(email, amountPaise, calendar.now()) != 1) {
            return Optional.empty();
        }

        return Optional.of(record(email, WalletTxnType.DEBIT, source, amountPaise, referenceId, description));
    }

    // ---------------------------------------------------------------- top-ups

    /** Step 1: create a Razorpay order for a top-up of a whole-rupee amount the customer chose. */
    @Transactional
    public PaymentOrderResponse createTopup(String email, Integer amountRupees) throws Exception {

        if (amountRupees == null) {
            throw new ApiException("Enter an amount to add.", HttpStatus.BAD_REQUEST);
        }

        long amountPaise = amountRupees * 100L;

        if (amountPaise < MIN_TOPUP_PAISE || amountPaise > MAX_TOPUP_PAISE) {
            throw new ApiException("You can add between " + Money.label(MIN_TOPUP_PAISE) + " and "
                    + Money.label(MAX_TOPUP_PAISE) + " at a time.", HttpStatus.BAD_REQUEST);
        }

        if (balancePaise(email) + amountPaise > MAX_BALANCE_PAISE) {
            throw new ApiException("A wallet can hold at most " + Money.label(MAX_BALANCE_PAISE) + ".",
                    HttpStatus.BAD_REQUEST);
        }

        return paymentService.createPaymentOrder(email, amountPaise, PaymentOrder.PURPOSE_WALLET_TOPUP);
    }

    /**
     * Step 2: credit the wallet, but only for a payment we can prove.
     *
     * The amount credited is the one WE recorded when creating the order - the
     * browser does not get to say how much it paid.
     */
    @Transactional
    public WalletTransaction verifyTopup(String email, PlaceOrderRequest request) {

        if (request == null) {
            throw new PaymentVerificationException("Payment details are required.");
        }

        PaymentOrder paymentOrder = paymentService.requireOwnPaymentOrder(email, request.getRazorpayOrderId());

        if (!paymentOrder.isWalletTopup()) {
            throw new PaymentVerificationException("This payment is not a wallet top-up.");
        }

        paymentService.verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());

        if (!paymentService.consume(paymentOrder.getId(), request.getRazorpayPaymentId())) {
            throw new PaymentVerificationException("This payment has already been added to your wallet.");
        }

        WalletTransaction tx = credit(email, paymentOrder.getAmountPaise(), WalletTxnSource.TOPUP,
                request.getRazorpayPaymentId(), "Added via Razorpay");

        log.info("Wallet top-up {} for {} ({} paise)", request.getRazorpayPaymentId(), email,
                paymentOrder.getAmountPaise());

        emailService.sendWalletTopupReceipt(email, tx.getAmountPaise(), tx.getBalanceAfterPaise(),
                request.getRazorpayPaymentId());

        return tx;
    }

    // ---------------------------------------------------------------- helpers

    private WalletTransaction record(String email, WalletTxnType type, WalletTxnSource source,
                                     long amountPaise, String referenceId, String description) {
        // Read after our UPDATE, inside the same transaction and while we hold
        // the row lock, so this is exactly the balance our change produced.
        long balanceAfter = walletRepository.findBalance(email).orElse(0L);

        return transactionRepository.save(new WalletTransaction(
                email, type, source, amountPaise, balanceAfter, referenceId, description, calendar.now()));
    }

    private void requirePositive(long amountPaise) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Wallet amounts must be positive");
        }
    }

    public static WalletTransactionResponse toResponse(WalletTransaction t) {
        return new WalletTransactionResponse(
                t.getId(),
                t.getType().name(),
                t.getSource().name(),
                Money.toRupees(t.getAmountPaise()),
                Money.toRupees(t.getBalanceAfterPaise()),
                t.getDescription(),
                t.getReferenceId(),
                t.getCreatedAt());
    }
}
