package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.dto.OrderStatsResponse;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.model.OrderStatus;
import com.kamaldairy.kamal_dairy_backend.model.WalletTxnSource;
import com.kamaldairy.kamal_dairy_backend.repository.OrderRepository;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything that happens to a cart order after it is paid.
 *
 * Every change locks the order row first, so an admin pressing "Delivered"
 * and a customer pressing "Cancel" at the same moment are applied one after
 * the other - never both. A cancel refunds the full amount to the Kamal
 * Wallet and puts the items back on the shelf, in the same transaction.
 */
@Service
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REASON = 200;

    public static final String BY_CUSTOMER = "CUSTOMER";
    public static final String BY_ADMIN = "ADMIN";

    private final OrderRepository orderRepository;
    private final WalletService walletService;
    private final StockService stockService;
    private final EmailService emailService;
    private final InvoiceService invoiceService;
    private final DeliveryCalendar calendar;

    public OrderLifecycleService(OrderRepository orderRepository,
                                 WalletService walletService,
                                 StockService stockService,
                                 EmailService emailService,
                                 InvoiceService invoiceService,
                                 DeliveryCalendar calendar) {
        this.orderRepository = orderRepository;
        this.walletService = walletService;
        this.stockService = stockService;
        this.emailService = emailService;
        this.invoiceService = invoiceService;
        this.calendar = calendar;
    }

    // ------------------------------------------------------------ customer

    /** A customer can cancel only until the dairy confirms the order. */
    @Transactional
    public Order cancelByCustomer(String userEmail, Integer orderId, String reason) {
        Order order = lock(orderId);

        // 404, not 403: never confirm that somebody else's order id exists.
        if (!order.getUserEmail().equalsIgnoreCase(userEmail)) {
            throw new ResourceNotFoundException("Order");
        }
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ApiException(order.getStatus() == OrderStatus.CANCELLED
                    ? "This order is already cancelled."
                    : "Order #" + order.getId() + " is already " + order.getStatus().label().toLowerCase(Locale.ENGLISH)
                      + ", so it can no longer be cancelled from the app. Please call us and we will help.",
                    HttpStatus.CONFLICT);
        }

        return cancel(order, BY_CUSTOMER, clean(reason, "Cancelled by customer"));
    }

    // --------------------------------------------------------------- admin

    @Transactional
    public Order advance(Integer orderId, String rawStatus) {
        OrderStatus next = parse(rawStatus);
        if (next == OrderStatus.CANCELLED) {
            throw new ApiException("Use cancel to cancel an order - it refunds the customer.", HttpStatus.BAD_REQUEST);
        }

        Order order = lock(orderId);
        OrderStatus current = order.getStatus();

        if (!current.canAdvanceTo(next)) {
            throw new ApiException("Order #" + order.getId() + " is " + current.label().toLowerCase(Locale.ENGLISH)
                    + " and cannot be moved to " + next.label().toLowerCase(Locale.ENGLISH) + ".", HttpStatus.CONFLICT);
        }

        order.moveTo(next, calendar.now());
        log.info("Order {} moved {} -> {}", order.getId(), current, next);

        if (next == OrderStatus.DELIVERED) {
            // Delivery is the moment the supply is complete, so this is when the
            // tax invoice is issued - inside this transaction, with the row still
            // locked, so the number and the status can never disagree. Drawing the
            // PDF is separate and best-effort; the email still goes out if it
            // fails, and the document stays downloadable from My Orders.
            invoiceService.issueOnDelivery(order);
            emailService.sendOrderDelivered(order, invoiceService.renderQuietly(order),
                    invoiceFileName(order));
        } else if (next == OrderStatus.OUT_FOR_DELIVERY) {
            emailService.sendOrderStatus(order);
        }
        return order;
    }

    private static String invoiceFileName(Order order) {
        String stem = order.getInvoiceNo() == null
                ? "order-" + order.getId()
                : order.getInvoiceNo().replaceAll("[^A-Za-z0-9]+", "-");
        return "Kamal-Dairy-invoice-" + stem + ".pdf";
    }

    @Transactional
    public Order cancelByAdmin(Integer orderId, String reason) {
        Order order = lock(orderId);
        if (!order.getStatus().isOpen()) {
            throw new ApiException("Order #" + order.getId() + " is already "
                    + order.getStatus().label().toLowerCase(Locale.ENGLISH) + ".", HttpStatus.CONFLICT);
        }
        return cancel(order, BY_ADMIN, clean(reason, "Cancelled by Kamal Dairy"));
    }

    @Transactional(readOnly = true)
    public Page<Order> list(String rawStatus, int page, int size) {
        PageRequest request = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));

        Page<Order> orders;
        if (rawStatus == null || rawStatus.isBlank() || rawStatus.equalsIgnoreCase("ALL")) {
            orders = orderRepository.findAll(request);
        } else if (rawStatus.equalsIgnoreCase("OPEN")) {
            orders = orderRepository.findByStatusIn(
                    List.of(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.OUT_FOR_DELIVERY), request);
        } else {
            OrderStatus status = parse(rawStatus);
            // Orders from before the lifecycle existed have no status and count as delivered.
            orders = status == OrderStatus.DELIVERED
                    ? orderRepository.findByStatusOrStatusIsNull(status, request)
                    : orderRepository.findByStatus(status, request);
        }

        orders.forEach(o -> o.getItems().size()); // load items while the session is open
        return orders;
    }

    @Transactional(readOnly = true)
    public OrderStatsResponse stats() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (Object[] row : orderRepository.countByStatus()) {
            OrderStatus status = row[0] == null ? OrderStatus.DELIVERED : (OrderStatus) row[0];
            counts.merge(status, ((Number) row[1]).longValue(), Long::sum);
        }
        long placed = counts.getOrDefault(OrderStatus.PLACED, 0L);
        long confirmed = counts.getOrDefault(OrderStatus.CONFIRMED, 0L);
        long out = counts.getOrDefault(OrderStatus.OUT_FOR_DELIVERY, 0L);

        return new OrderStatsResponse(placed, confirmed, out,
                counts.getOrDefault(OrderStatus.DELIVERED, 0L),
                counts.getOrDefault(OrderStatus.CANCELLED, 0L),
                placed + confirmed + out,
                stockService.countLowStock());
    }

    // ------------------------------------------------------------- helpers

    private Order cancel(Order order, String by, String reason) {
        long refundPaise = Money.toPaise(order.getTotalAmount());

        order.cancel(calendar.now(), by, reason, refundPaise);
        stockService.putBack(order.getItems());

        if (refundPaise > 0) {
            walletService.credit(order.getUserEmail(), refundPaise, WalletTxnSource.REFUND,
                    "ORDER-" + order.getId(), "Refund for order #" + order.getId());
        }

        log.info("Order {} cancelled by {} - {} refunded to wallet", order.getId(), by, Money.label(refundPaise));
        emailService.sendOrderCancelled(order, refundPaise);
        return order;
    }

    private Order lock(Integer orderId) {
        if (orderId == null) {
            throw new ApiException("Order id is required.", HttpStatus.BAD_REQUEST);
        }
        Order order = orderRepository.findForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order"));
        order.getItems().size(); // needed for restock and for the response
        return order;
    }

    private static OrderStatus parse(String raw) {
        try {
            return OrderStatus.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException("Unknown order status: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    private static String clean(String reason, String fallback) {
        String r = reason == null ? "" : reason.trim().replaceAll("\\s+", " ");
        if (r.isEmpty()) {
            return fallback;
        }
        return r.length() > MAX_REASON ? r.substring(0, MAX_REASON) : r;
    }
}
