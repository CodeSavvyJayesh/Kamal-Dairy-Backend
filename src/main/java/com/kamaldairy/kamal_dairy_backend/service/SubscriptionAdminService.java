package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.AdminDeliveryResponse;
import com.kamaldairy.kamal_dairy_backend.dto.DispatchResponse;
import com.kamaldairy.kamal_dairy_backend.dto.SubscriptionStatsResponse;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.*;
import com.kamaldairy.kamal_dairy_backend.repository.*;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SubscriptionAdminService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionAdminService.class);

    private static final DateTimeFormatter LEDGER_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final Set<DeliveryStatus> PAID = Set.of(DeliveryStatus.SCHEDULED, DeliveryStatus.DELIVERED);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionSkipRepository skipRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final SubscriptionGenerationRunRepository runRepository;
    private final ProductRepository productRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final EmailService emailService;
    private final DeliveryCalendar calendar;

    public SubscriptionAdminService(SubscriptionRepository subscriptionRepository,
                                    SubscriptionSkipRepository skipRepository,
                                    SubscriptionDeliveryRepository deliveryRepository,
                                    SubscriptionGenerationRunRepository runRepository,
                                    ProductRepository productRepository,
                                    WalletRepository walletRepository,
                                    WalletService walletService,
                                    EmailService emailService,
                                    DeliveryCalendar calendar) {
        this.subscriptionRepository = subscriptionRepository;
        this.skipRepository = skipRepository;
        this.deliveryRepository = deliveryRepository;
        this.runRepository = runRepository;
        this.productRepository = productRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.emailService = emailService;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public SubscriptionStatsResponse stats() {
        LocalDate today = calendar.today();
        LocalDate tomorrow = today.plusDays(1);
        boolean tomorrowGenerated = runRepository.existsByDeliveryDate(tomorrow);

        int tomorrowCount;
        long tomorrowPaise;

        if (tomorrowGenerated) {
            tomorrowCount = deliveryRepository.countByDeliveryDateAndStatusIn(tomorrow, PAID);
            tomorrowPaise = deliveryRepository.sumAmount(tomorrow, PAID);
        } else {
            tomorrowCount = 0;
            tomorrowPaise = 0;
        }

        // Projected monthly recurring revenue, and tomorrow's projection when it
        // has not been generated yet - both at today's prices.
        long mrr = 0;
        LocalDate from = calendar.firstEditableDate();

        for (Subscription s : subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)) {
            Product p = productRepository.findById(s.getProductId()).orElse(null);
            if (p == null) {
                continue;
            }

            long per = Money.discounted(Money.toPaise(p.getPrice()), s.getQuantity(),
                    s.getFrequency().getDiscountPercent());

            LocalDate monthFrom = from.isBefore(s.getStartDate()) ? s.getStartDate() : from;
            mrr += per * SubscriptionSchedule.count(s.getFrequency(), s.getDaysOfWeekMask(), s.getStartDate(),
                    monthFrom, monthFrom.plusDays(29));

            if (!tomorrowGenerated
                    && SubscriptionSchedule.occursOn(s.getFrequency(), s.getDaysOfWeekMask(), s.getStartDate(), tomorrow)
                    && !s.isOnVacation(tomorrow)
                    && !skipRepository.existsBySubscriptionIdAndSkipDate(s.getId(), tomorrow)) {
                tomorrowCount++;
                tomorrowPaise += per;
            }
        }

        return new SubscriptionStatsResponse(
                subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE),
                subscriptionRepository.countByStatus(SubscriptionStatus.PAUSED),
                subscriptionRepository.countByStatus(SubscriptionStatus.CANCELLED),
                today,
                deliveryRepository.countByDeliveryDateAndStatusIn(today, PAID),
                tomorrow,
                tomorrowGenerated,
                tomorrowCount,
                Money.toRupees(tomorrowPaise),
                Money.toRupees(mrr),
                Money.toRupees(walletRepository.totalBalancePaise()),
                calendar.cutoffHour());
    }

    /** The packing / route sheet for one date: morning slot first, then by product. */
    @Transactional(readOnly = true)
    public DispatchResponse dispatch(LocalDate date) {
        List<SubscriptionDelivery> rows = deliveryRepository.findByDeliveryDate(date).stream()
                .sorted(Comparator.comparing((SubscriptionDelivery d) -> d.getSlot().ordinal())
                        .thenComparing(SubscriptionDelivery::getProductName)
                        .thenComparing(SubscriptionDelivery::getId))
                .toList();

        int scheduled = 0, delivered = 0, missed = 0, refunded = 0, units = 0;
        long revenue = 0;

        for (SubscriptionDelivery d : rows) {
            switch (d.getStatus()) {
                case SCHEDULED -> scheduled++;
                case DELIVERED -> delivered++;
                case MISSED -> missed++;
                case REFUNDED -> refunded++;
            }
            if (PAID.contains(d.getStatus())) {
                units += d.getQuantity();
                revenue += d.getAmountPaise();
            }
        }

        return new DispatchResponse(date, runRepository.existsByDeliveryDate(date), rows.size(),
                scheduled, delivered, missed, refunded, units, Money.toRupees(revenue),
                rows.stream().map(SubscriptionAdminService::toAdmin).toList());
    }

    @Transactional
    public AdminDeliveryResponse markDelivered(Long id) {
        SubscriptionDelivery d = deliveryRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery"));

        if (d.getStatus() != DeliveryStatus.SCHEDULED) {
            throw bad("Only a scheduled delivery can be marked as delivered.");
        }
        if (d.getDeliveryDate().isAfter(calendar.today())) {
            throw bad("This delivery is for " + d.getDeliveryDate() + " and cannot be marked delivered yet.");
        }

        d.setStatus(DeliveryStatus.DELIVERED);
        d.setNote("Delivered");
        d.setUpdatedAt(calendar.now());
        return toAdmin(d);
    }

    /**
     * Refund a paid delivery to the customer's wallet. The row lock stops two
     * admins clicking at once from refunding it twice.
     */
    @Transactional
    public AdminDeliveryResponse refund(Long id, String reason) {
        SubscriptionDelivery d = deliveryRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery"));

        if (!PAID.contains(d.getStatus())) {
            throw bad("Only a paid delivery can be refunded.");
        }
        if (d.getAmountPaise() <= 0 || d.getWalletTransactionId() == null) {
            throw bad("Nothing was charged for this delivery.");
        }

        String cleanReason = reason == null ? "" : reason.trim();

        WalletTransaction tx = walletService.credit(d.getUserEmail(), d.getAmountPaise(), WalletTxnSource.REFUND,
                "DEL-" + d.getId(), "Refund · " + d.getProductName() + " · "
                        + LEDGER_DATE.format(d.getDeliveryDate()));

        d.setStatus(DeliveryStatus.REFUNDED);
        d.setRefundTransactionId(tx.getId());
        d.setNote(cleanReason.isEmpty() ? "Refunded to wallet" : "Refunded: " + cleanReason);
        d.setUpdatedAt(calendar.now());

        emailService.sendDeliveryRefunded(d.getUserEmail(), d.getProductName(), d.getDeliveryDate(),
                d.getAmountPaise(), cleanReason);

        log.info("Delivery {} refunded ({} paise) to {}", d.getId(), d.getAmountPaise(), d.getUserEmail());
        return toAdmin(d);
    }

    private static AdminDeliveryResponse toAdmin(SubscriptionDelivery d) {
        return new AdminDeliveryResponse(
                d.getId(), d.getSubscriptionId(), d.getUserEmail(), d.getDeliveryName(), d.getDeliveryPhone(),
                d.getDeliveryAddress(), d.getProductName(), d.getQuantity(), Money.toRupees(d.getAmountPaise()),
                d.getSlot().name(), d.getSlot().getLabel(), d.getStatus().name(), d.getNote());
    }

    private static ApiException bad(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }
}
