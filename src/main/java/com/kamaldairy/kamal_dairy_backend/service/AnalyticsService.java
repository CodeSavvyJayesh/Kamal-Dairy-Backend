package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.SalesAnalyticsResponse;
import com.kamaldairy.kamal_dairy_backend.dto.SalesAnalyticsResponse.*;
import com.kamaldairy.kamal_dairy_backend.dto.SubscriptionStatsResponse;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.model.*;
import com.kamaldairy.kamal_dairy_backend.repository.OrderRepository;
import com.kamaldairy.kamal_dairy_backend.repository.SubscriptionDeliveryRepository;
import com.kamaldairy.kamal_dairy_backend.repository.SubscriptionRepository;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Sales analytics for the admin dashboard.
 *
 * Revenue counts what the dairy actually keeps:
 *  - cart orders by the day they were placed, unless cancelled (a cancel is
 *    refunded in full);
 *  - subscription deliveries by delivery day, when charged (SCHEDULED or
 *    DELIVERED). MISSED was never charged; REFUNDED went back.
 *
 * Everything is summed in paise and converted once at the end.
 */
@Service
public class AnalyticsService {

    public static final int MIN_DAYS = 7;
    public static final int MAX_DAYS = 365;
    private static final int TOP_PRODUCTS = 8;

    private static final Set<DeliveryStatus> CHARGED = Set.of(DeliveryStatus.SCHEDULED, DeliveryStatus.DELIVERED);

    private static final List<String> PLAN_ORDER = List.of("daily", "weekly", "monthly");

    private final OrderRepository orderRepository;
    private final SubscriptionDeliveryRepository deliveryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAdminService subscriptionAdminService;
    private final DeliveryCalendar calendar;

    public AnalyticsService(OrderRepository orderRepository,
                            SubscriptionDeliveryRepository deliveryRepository,
                            SubscriptionRepository subscriptionRepository,
                            SubscriptionAdminService subscriptionAdminService,
                            DeliveryCalendar calendar) {
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionAdminService = subscriptionAdminService;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public SalesAnalyticsResponse sales(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new ApiException("Choose between " + MIN_DAYS + " and " + MAX_DAYS + " days.", HttpStatus.BAD_REQUEST);
        }

        LocalDate to = calendar.today();
        LocalDate from = to.minusDays(days - 1L);
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1L);

        // One read for both periods.
        List<Order> orders = orderRepository.findWithItemsPlacedBetween(
                prevFrom.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<SubscriptionDelivery> deliveries = deliveryRepository.findByDeliveryDateBetween(prevFrom, to);

        Map<String, LocalDate> firstPurchase = firstPurchasePerCustomer();

        Totals current = totals(orders, deliveries, from, to, firstPurchase);
        Totals previous = totals(orders, deliveries, prevFrom, prevTo, firstPurchase);

        List<Order> inRange = orders.stream().filter(o -> within(dayOf(o), from, to)).toList();
        List<SubscriptionDelivery> dInRange = deliveries.stream()
                .filter(d -> within(d.getDeliveryDate(), from, to)).toList();

        return new SalesAnalyticsResponse(
                from, to, days,
                current, previous,
                daily(inRange, dInRange, from, to),
                topProducts(inRange, dInRange),
                plans(dInRange),
                customers(current),
                payments(inRange, dInRange),
                snapshot());
    }

    // ------------------------------------------------------------ sections

    private Totals totals(List<Order> orders, List<SubscriptionDelivery> deliveries,
                          LocalDate from, LocalDate to, Map<String, LocalDate> firstPurchase) {
        long cart = 0, subs = 0, refunded = 0;
        long orderCount = 0, cancelled = 0, deliveryCount = 0;
        Set<String> buyers = new HashSet<>();

        for (Order o : orders) {
            if (!within(dayOf(o), from, to)) continue;
            if (o.getStatus() == OrderStatus.CANCELLED) {
                cancelled++;
                refunded += Money.toPaise(o.getTotalAmount());
                continue;
            }
            orderCount++;
            cart += Money.toPaise(o.getTotalAmount());
            buyers.add(key(o.getUserEmail()));
        }

        for (SubscriptionDelivery d : deliveries) {
            if (!within(d.getDeliveryDate(), from, to)) continue;
            if (CHARGED.contains(d.getStatus())) {
                deliveryCount++;
                subs += d.getAmountPaise();
                buyers.add(key(d.getUserEmail()));
            } else if (d.getStatus() == DeliveryStatus.REFUNDED) {
                refunded += d.getAmountPaise();
            }
        }

        long newBuyers = buyers.stream()
                .filter(b -> within(firstPurchase.get(b), from, to))
                .count();

        long aov = orderCount == 0 ? 0 : Math.round((double) cart / orderCount);

        return new Totals(
                Money.toRupees(cart + subs), Money.toRupees(cart), Money.toRupees(subs),
                orderCount, deliveryCount, Money.toRupees(aov),
                cancelled, Money.toRupees(refunded),
                buyers.size(), newBuyers);
    }

    private List<Day> daily(List<Order> orders, List<SubscriptionDelivery> deliveries, LocalDate from, LocalDate to) {
        Map<LocalDate, long[]> byDay = new TreeMap<>(); // cart, subs, orders, deliveries
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new long[4]);
        }
        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.CANCELLED) continue;
            long[] v = byDay.get(dayOf(o));
            if (v != null) {
                v[0] += Money.toPaise(o.getTotalAmount());
                v[2]++;
            }
        }
        for (SubscriptionDelivery d : deliveries) {
            if (!CHARGED.contains(d.getStatus())) continue;
            long[] v = byDay.get(d.getDeliveryDate());
            if (v != null) {
                v[1] += d.getAmountPaise();
                v[3]++;
            }
        }

        List<Day> out = new ArrayList<>();
        byDay.forEach((date, v) -> out.add(new Day(date, Money.toRupees(v[0]), Money.toRupees(v[1]),
                Money.toRupees(v[0] + v[1]), v[2], v[3])));
        return out;
    }

    private List<ProductRow> topProducts(List<Order> orders, List<SubscriptionDelivery> deliveries) {
        // productId -> [revenuePaise, cartUnits, subUnits]
        Map<Integer, long[]> stats = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();

        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.CANCELLED || o.getItems() == null) continue;
            for (OrderItem i : o.getItems()) {
                if (i.getProductId() == null) continue;
                long[] v = stats.computeIfAbsent(i.getProductId(), k -> new long[3]);
                v[0] += Money.toPaise(i.getPrice()) * i.getQuantity();
                v[1] += i.getQuantity();
                names.putIfAbsent(i.getProductId(), i.getProductName());
            }
        }
        for (SubscriptionDelivery d : deliveries) {
            if (!CHARGED.contains(d.getStatus())) continue;
            long[] v = stats.computeIfAbsent(d.getProductId(), k -> new long[3]);
            v[0] += d.getAmountPaise();
            v[2] += d.getQuantity();
            names.putIfAbsent(d.getProductId(), d.getProductName());
        }

        return stats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(TOP_PRODUCTS)
                .map(e -> new ProductRow(e.getKey(), names.get(e.getKey()),
                        e.getValue()[1] + e.getValue()[2], Money.toRupees(e.getValue()[0]),
                        e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    private List<PlanRow> plans(List<SubscriptionDelivery> deliveries) {
        Set<Long> ids = new HashSet<>();
        for (SubscriptionDelivery d : deliveries) ids.add(d.getSubscriptionId());

        Map<Long, SubscriptionFrequency> frequencyOf = new HashMap<>();
        for (Subscription s : subscriptionRepository.findAllById(ids)) {
            frequencyOf.put(s.getId(), s.getFrequency());
        }

        Map<String, long[]> byPlan = new LinkedHashMap<>(); // deliveries, revenue, active
        for (String plan : PLAN_ORDER) byPlan.put(plan, new long[3]);

        for (SubscriptionDelivery d : deliveries) {
            if (!CHARGED.contains(d.getStatus())) continue;
            SubscriptionFrequency f = frequencyOf.get(d.getSubscriptionId());
            long[] v = byPlan.get(f == null ? "daily" : f.getPlan());
            v[0]++;
            v[1] += d.getAmountPaise();
        }
        for (Subscription s : subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)) {
            byPlan.get(s.getFrequency().getPlan())[2]++;
        }

        List<PlanRow> out = new ArrayList<>();
        byPlan.forEach((plan, v) -> out.add(new PlanRow(plan, planName(plan), v[0], Money.toRupees(v[1]), v[2])));
        return out;
    }

    private static Customers customers(Totals t) {
        long returning = t.buyers() - t.newBuyers();
        int rate = t.buyers() == 0 ? 0 : (int) Math.round(100.0 * returning / t.buyers());
        return new Customers(t.buyers(), t.newBuyers(), returning, rate);
    }

    private static Payments payments(List<Order> orders, List<SubscriptionDelivery> deliveries) {
        long online = 0, wallet = 0, subs = 0;
        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.CANCELLED) continue;
            if (Order.PAY_WALLET.equals(o.getPaymentMethod())) wallet += Money.toPaise(o.getTotalAmount());
            else online += Money.toPaise(o.getTotalAmount());
        }
        for (SubscriptionDelivery d : deliveries) {
            if (CHARGED.contains(d.getStatus())) subs += d.getAmountPaise();
        }
        return new Payments(Money.toRupees(online), Money.toRupees(wallet), Money.toRupees(subs));
    }

    private SubscriptionSnapshot snapshot() {
        SubscriptionStatsResponse s = subscriptionAdminService.stats();
        return new SubscriptionSnapshot(s.activeSubscriptions(), s.pausedSubscriptions(),
                s.monthlyRecurringRevenue(), s.walletFloat());
    }

    // ------------------------------------------------------------ helpers

    /** Earliest purchase of any kind, per customer (lower-cased email). */
    private Map<String, LocalDate> firstPurchasePerCustomer() {
        Map<String, LocalDate> first = new HashMap<>();
        for (Object[] row : orderRepository.firstOrderPerCustomer(OrderStatus.CANCELLED)) {
            if (row[0] != null && row[1] != null) {
                first.merge(key((String) row[0]), ((LocalDateTime) row[1]).toLocalDate(), AnalyticsService::earlier);
            }
        }
        for (Object[] row : deliveryRepository.firstChargedDeliveryPerCustomer(CHARGED)) {
            if (row[0] != null && row[1] != null) {
                first.merge(key((String) row[0]), (LocalDate) row[1], AnalyticsService::earlier);
            }
        }
        return first;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate dayOf(Order o) {
        return o.getCreatedAt() == null ? null : o.getCreatedAt().toLocalDate();
    }

    private static boolean within(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String planName(String plan) {
        return switch (plan) {
            case "weekly" -> "Weekly Essentials";
            case "monthly" -> "Monthly Smart Saver";
            default -> "Daily Delivery";
        };
    }
}
