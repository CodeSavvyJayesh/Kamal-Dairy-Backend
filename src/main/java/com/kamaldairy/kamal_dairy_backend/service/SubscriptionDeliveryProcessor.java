package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.*;
import com.kamaldairy.kamal_dairy_backend.repository.ProductRepository;
import com.kamaldairy.kamal_dairy_backend.repository.SubscriptionDeliveryRepository;
import com.kamaldairy.kamal_dairy_backend.repository.SubscriptionRepository;
import com.kamaldairy.kamal_dairy_backend.repository.SubscriptionSkipRepository;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns one subscription + one date into one delivery.
 *
 * A separate bean so that each call runs in its OWN transaction
 * (REQUIRES_NEW through the Spring proxy). One customer's failure - a bad row,
 * a deleted product - rolls back that customer only, never the whole night.
 */
@Component
public class SubscriptionDeliveryProcessor {

    public enum Outcome { NOT_DUE, ALREADY_EXISTS, CHARGED, MISSED_LOW_BALANCE, MISSED_UNAVAILABLE }

    private static final DateTimeFormatter LEDGER_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSkipRepository skipRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final ProductRepository productRepository;
    private final WalletService walletService;
    private final EmailService emailService;
    private final DeliveryCalendar calendar;

    public SubscriptionDeliveryProcessor(SubscriptionRepository subscriptionRepository,
                                         SubscriptionSkipRepository skipRepository,
                                         SubscriptionDeliveryRepository deliveryRepository,
                                         ProductRepository productRepository,
                                         WalletService walletService,
                                         EmailService emailService,
                                         DeliveryCalendar calendar) {
        this.subscriptionRepository = subscriptionRepository;
        this.skipRepository = skipRepository;
        this.deliveryRepository = deliveryRepository;
        this.productRepository = productRepository;
        this.walletService = walletService;
        this.emailService = emailService;
        this.calendar = calendar;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome process(Long subscriptionId, LocalDate date) {

        Subscription s = subscriptionRepository.findById(subscriptionId).orElse(null);

        if (s == null || s.getStatus() != SubscriptionStatus.ACTIVE) {
            return Outcome.NOT_DUE;
        }
        if (!SubscriptionSchedule.occursOn(s.getFrequency(), s.getDaysOfWeekMask(), s.getStartDate(), date)) {
            return Outcome.NOT_DUE;
        }
        if (s.isOnVacation(date) || skipRepository.existsBySubscriptionIdAndSkipDate(s.getId(), date)) {
            return Outcome.NOT_DUE;
        }
        if (deliveryRepository.existsBySubscriptionIdAndDeliveryDate(s.getId(), date)) {
            return Outcome.ALREADY_EXISTS;
        }

        LocalDateTime now = calendar.now();

        SubscriptionDelivery d = new SubscriptionDelivery();
        d.setSubscriptionId(s.getId());
        d.setUserEmail(s.getUserEmail());
        d.setProductId(s.getProductId());
        d.setProductName(s.getProductName());
        d.setQuantity(s.getQuantity());
        d.setDiscountPercent(s.getFrequency().getDiscountPercent());
        d.setDeliveryDate(date);
        d.setSlot(s.getSlot());
        d.setDeliveryName(s.getDeliveryName());
        d.setDeliveryPhone(s.getDeliveryPhone());
        d.setDeliveryAddress(s.fullAddress());
        d.setStatus(DeliveryStatus.SCHEDULED);
        d.setCreatedAt(now);
        d.setUpdatedAt(now);

        Product product = productRepository.findById(s.getProductId()).orElse(null);

        if (product == null) {
            d.setUnitPricePaise(0);
            d.setAmountPaise(0);
            d.setStatus(DeliveryStatus.MISSED);
            d.setNote("This product is no longer available");
            deliveryRepository.saveAndFlush(d);
            return Outcome.MISSED_UNAVAILABLE;
        }

        long unit = Money.toPaise(product.getPrice());
        long amount = Money.discounted(unit, s.getQuantity(), s.getFrequency().getDiscountPercent());

        d.setProductName(product.getName());
        d.setUnitPricePaise(unit);
        d.setAmountPaise(amount);

        // Claim the (subscription, date) slot BEFORE touching money. If another
        // run already got here, the unique key makes this insert throw and the
        // whole transaction - debit included - never happens.
        deliveryRepository.saveAndFlush(d);

        if (amount <= 0) {
            return Outcome.CHARGED;
        }

        Optional<WalletTransaction> tx = walletService.tryDebit(
                s.getUserEmail(), amount, WalletTxnSource.SUBSCRIPTION, "DEL-" + d.getId(),
                product.getName() + " ×" + s.getQuantity() + " · " + LEDGER_DATE.format(date));

        if (tx.isEmpty()) {
            d.setStatus(DeliveryStatus.MISSED);
            d.setNote("Insufficient wallet balance");
            d.setUpdatedAt(calendar.now());

            emailService.sendDeliveryMissed(s.getUserEmail(), product.getName(), date, amount,
                    walletService.balancePaise(s.getUserEmail()));

            return Outcome.MISSED_LOW_BALANCE;
        }

        d.setWalletTransactionId(tx.get().getId());
        d.setUpdatedAt(calendar.now());
        return Outcome.CHARGED;
    }

    /** Close out yesterday and earlier: still-SCHEDULED deliveries went out. */
    @Transactional
    public int autoConfirmBefore(LocalDate today) {
        return deliveryRepository.autoConfirmBefore(today, DeliveryStatus.SCHEDULED, DeliveryStatus.DELIVERED,
                "Delivered", calendar.now());
    }
}
