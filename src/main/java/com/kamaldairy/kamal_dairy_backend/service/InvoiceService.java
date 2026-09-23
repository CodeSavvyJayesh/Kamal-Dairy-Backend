package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.config.InvoiceProperties;
import com.kamaldairy.kamal_dairy_backend.dto.InvoiceView;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.exception.ResourceNotFoundException;
import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.model.OrderItem;
import com.kamaldairy.kamal_dairy_backend.model.OrderStatus;
import com.kamaldairy.kamal_dairy_backend.repository.OrderRepository;
import com.kamaldairy.kamal_dairy_backend.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the invoice for a cart order and renders it as a PDF.
 *
 * Three documents come out of the same order, depending on where it is:
 *
 *   - delivered, dairy GST-registered  -> TAX INVOICE, numbered, with CGST/SGST
 *   - delivered, not registered        -> BILL OF SUPPLY, numbered, no tax shown
 *   - still open                       -> PROFORMA, unnumbered, clearly marked
 *
 * A number is only ever spent on a real document, and only once. Cancelled
 * orders get nothing: there is no supply to invoice and the money has already
 * gone back to the wallet.
 *
 * Prices on the shelf INCLUDE GST, which is how retail works in India, so the
 * tax is extracted out of each line rather than added on top. Everything is done
 * in whole paise with integer arithmetic, and the totals are reconciled against
 * the order total before the document is drawn.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private static final DateTimeFormatter CSV_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrderRepository orderRepository;
    private final InvoiceNumberService numbers;
    private final InvoiceRenderer renderer;
    private final InvoiceProperties properties;
    private final DeliveryCalendar calendar;

    public InvoiceService(OrderRepository orderRepository,
                          InvoiceNumberService numbers,
                          InvoiceRenderer renderer,
                          InvoiceProperties properties,
                          DeliveryCalendar calendar) {
        this.orderRepository = orderRepository;
        this.numbers = numbers;
        this.renderer = renderer;
        this.properties = properties;
        this.calendar = calendar;
    }

    // ---------------------------------------------------------------- public

    /** The caller's own invoice. 404 on somebody else's order id, never 403. */
    @Transactional
    public Rendered forCustomer(String userEmail, Integer orderId) {
        Order order = load(orderId);
        if (!order.getUserEmail().equalsIgnoreCase(userEmail)) {
            throw new ResourceNotFoundException("Order");
        }
        return render(order);
    }

    @Transactional
    public Rendered forAdmin(Integer orderId) {
        return render(load(orderId));
    }

    /**
     * Stamps the tax invoice number on an order that has just been delivered.
     *
     * Called from the lifecycle inside its transaction, with the order row
     * already locked, so the number and the DELIVERED status commit together or
     * not at all. Idempotent: an order that somehow arrives here twice keeps its
     * first number. A failure here deliberately fails the whole delivery - an
     * order marked delivered with no invoice would be a hole in the register.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String issueOnDelivery(Order order) {
        if (!order.isInvoiced()) {
            LocalDateTime at = calendar.now();
            order.stampInvoice(numbers.allocate(at.toLocalDate()), at);
            log.info("Invoice {} issued for order {}", order.getInvoiceNo(), order.getId());
        }
        return order.getInvoiceNo();
    }

    /**
     * Renders an already-stamped order for the delivery email, returning null if
     * anything goes wrong.
     *
     * Not transactional, and that is the point: drawing the PDF is pure layout
     * over an order already in memory, so a fault in it must not mark the
     * delivery's transaction rollback-only. The customer can always download the
     * document from My Orders instead.
     */
    public byte[] renderQuietly(Order order) {
        try {
            return renderer.render(build(order));
        } catch (RuntimeException e) {
            log.error("Could not render the invoice for order {}", order.getId(), e);
            return null;
        }
    }

    /** Every issued invoice in a date range, as CSV for the accountant. */
    @Transactional(readOnly = true)
    public String csv(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException("Both from and to dates are required.", HttpStatus.BAD_REQUEST);
        }
        if (to.isBefore(from)) {
            throw new ApiException("The to date cannot be before the from date.", HttpStatus.BAD_REQUEST);
        }
        if (from.plusYears(2).isBefore(to)) {
            throw new ApiException("Please export at most two years at a time.", HttpStatus.BAD_REQUEST);
        }

        List<Order> orders = orderRepository.findInvoicedBetween(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        StringBuilder sb = new StringBuilder(1024);
        sb.append("Invoice No,Invoice Date,Order No,Order Date,Customer,City,Pincode,"
                + "Payment,HSN,Item,Qty,GST %,Taxable,CGST,SGST,Line Total\n");

        for (Order order : orders) {
            InvoiceView view = build(order);
            for (InvoiceView.Line line : view.lines()) {
                sb.append(csv(view.number())).append(',')
                  .append(view.issuedOn() == null ? "" : CSV_DAY.format(view.issuedOn())).append(',')
                  .append(order.getId()).append(',')
                  .append(order.getCreatedAt() == null ? "" : CSV_DAY.format(order.getCreatedAt())).append(',')
                  .append(csv(view.buyer().name())).append(',')
                  .append(csv(view.buyer().city())).append(',')
                  .append(csv(view.buyer().pincode())).append(',')
                  .append(csv(view.paymentMethod())).append(',')
                  .append(csv(line.hsn())).append(',')
                  .append(csv(line.description())).append(',')
                  .append(line.quantity()).append(',')
                  .append(line.gstRatePercent()).append(',')
                  .append(Money.toRupees(line.taxablePaise())).append(',')
                  .append(Money.toRupees(line.cgstPaise())).append(',')
                  .append(Money.toRupees(line.sgstPaise())).append(',')
                  .append(Money.toRupees(line.grossPaise())).append('\n');
            }
        }
        return sb.toString();
    }

    /** A rendered document: the bytes, and the name the browser should save. */
    public record Rendered(byte[] pdf, String fileName, String invoiceNumber) {}

    // --------------------------------------------------------------- internal

    private Order load(Integer orderId) {
        if (orderId == null) {
            throw new ApiException("Order id is required.", HttpStatus.BAD_REQUEST);
        }
        // Locked, because a download of a delivered order may have to issue the
        // number - see render().
        Order order = orderRepository.findForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order"));
        order.getItems().size(); // load the lines while the session is open
        return order;
    }

    /**
     * Renders, issuing a number first if this is a delivered order that does not
     * have one - which covers orders delivered before invoicing existed, and any
     * order whose delivery email failed before the stamp was written.
     */
    private Rendered render(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(
                    "Order #" + order.getId() + " was cancelled and refunded, so there is no invoice for it.",
                    HttpStatus.CONFLICT);
        }
        if (order.getStatus() == OrderStatus.DELIVERED && !order.isInvoiced()) {
            LocalDateTime at = order.getDeliveredAt() == null ? calendar.now() : order.getDeliveredAt();
            order.stampInvoice(numbers.allocate(at.toLocalDate()), at);
            log.info("Invoice {} issued for order {} on first download", order.getInvoiceNo(), order.getId());
        }
        InvoiceView view = build(order);
        return new Rendered(renderer.render(view), view.fileName(), view.number());
    }

    /**
     * Turns an order into the figures the invoice prints.
     *
     * Per line, with a GST-inclusive price and rate r:
     *   gross   = unit price x quantity
     *   taxable = gross x 100 / (100 + r)   rounded half up
     *   tax     = gross - taxable           so the line always adds back up
     *
     * Working from gross downwards rather than taxable upwards is what keeps the
     * printed total equal to the amount actually charged, to the paisa.
     *
     * One extra step: an odd number of paise of tax cannot be halved evenly, and
     * an invoice where CGST and SGST differ by a paisa is a question from the
     * auditor. So the odd paisa is moved into the taxable value instead, leaving
     * an even tax and CGST exactly equal to SGST - on every line, and therefore
     * in every total.
     */
    InvoiceView build(Order order) {

        boolean registered = properties.isGstRegistered();
        boolean delivered = order.getStatus() == OrderStatus.DELIVERED;

        InvoiceView.Kind kind = !delivered
                ? InvoiceView.Kind.PROFORMA
                : registered ? InvoiceView.Kind.TAX_INVOICE : InvoiceView.Kind.BILL_OF_SUPPLY;

        // A proforma is priced but not taxed: nothing has been supplied yet.
        boolean taxable = registered && delivered;

        List<InvoiceView.Line> lines = new ArrayList<>();
        Map<Integer, long[]> byRate = new TreeMap<>(); // rate -> {taxable, cgst, sgst}

        long linesTotal = 0;
        long taxableTotal = 0;
        long cgstTotal = 0;
        long sgstTotal = 0;
        int serial = 1;

        List<OrderItem> items = order.getItems() == null ? List.of() : order.getItems();
        for (OrderItem item : items) {
            long unit = Money.toPaise(item.getPrice());
            long gross = unit * Math.max(0, item.getQuantity());
            int rate = taxable ? item.getGstRatePercent() : 0;

            long lineTaxable = gross;
            long cgst = 0;
            long sgst = 0;
            if (rate > 0) {
                lineTaxable = BigDecimal.valueOf(gross)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(100L + rate), 0, RoundingMode.HALF_UP)
                        .longValueExact();
                long tax = gross - lineTaxable;
                if (tax % 2 != 0) {
                    // Keep CGST and SGST identical: park the odd paisa in the
                    // taxable value, which still leaves taxable + tax == gross.
                    lineTaxable += 1;
                    tax -= 1;
                }
                cgst = tax / 2;
                sgst = tax / 2;
            }

            lines.add(new InvoiceView.Line(serial++, item.getProductName(),
                    item.getHsnCode() == null ? "" : item.getHsnCode(),
                    item.getQuantity(), unit, gross, rate, lineTaxable, cgst, sgst));

            if (taxable) {
                long[] row = byRate.computeIfAbsent(rate, r -> new long[3]);
                row[0] += lineTaxable;
                row[1] += cgst;
                row[2] += sgst;
            }

            linesTotal += gross;
            taxableTotal += lineTaxable;
            cgstTotal += cgst;
            sgstTotal += sgst;
        }

        // The order total is what the customer was actually charged. If the sum
        // of the lines drifts from it - a legacy order, a price edited to a
        // fraction of a paisa - the invoice shows the difference as a round-off
        // rather than quietly printing a total nobody paid.
        long charged = Money.toPaise(order.getTotalAmount());
        long roundOff = charged - linesTotal;
        long grandTotal = linesTotal + roundOff;

        List<InvoiceView.TaxRow> taxSummary = byRate.entrySet().stream()
                .map(e -> new InvoiceView.TaxRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingInt(InvoiceView.TaxRow::gstRatePercent))
                .toList();

        LocalDate issuedOn = order.getInvoicedAt() != null
                ? order.getInvoicedAt().toLocalDate()
                : calendar.today();

        return new InvoiceView(
                kind,
                order.getInvoiceNo(),
                issuedOn,
                order.getId(),
                order.getCreatedAt(),
                order.getStatusLabel(),
                Order.PAY_WALLET.equals(order.getPaymentMethod()) ? "Kamal Wallet" : "Online (Razorpay)",
                order.getRazorpayPaymentId(),
                seller(),
                buyer(order),
                lines,
                taxSummary,
                taxableTotal,
                cgstTotal,
                sgstTotal,
                grandTotal,
                roundOff,
                taxable);
    }

    private InvoiceView.Seller seller() {
        List<String> address = new ArrayList<>();
        addIfPresent(address, properties.getAddressLine1());
        addIfPresent(address, properties.getAddressLine2());

        String cityLine = join(properties.getCity(), properties.getPincode());
        addIfPresent(address, cityLine);
        addIfPresent(address, properties.getState());

        return new InvoiceView.Seller(
                properties.getSellerName(),
                blankToNull(properties.getGstin()),
                blankToNull(properties.getFssai()),
                address,
                blankToNull(properties.getPhone()),
                blankToNull(properties.getEmail()),
                blankToNull(properties.getState()));
    }

    private static InvoiceView.Buyer buyer(Order order) {
        return new InvoiceView.Buyer(
                order.getDeliveryName() == null ? order.getUserEmail() : order.getDeliveryName(),
                order.getDeliveryPhone(),
                order.getDeliveryAddress(),
                order.getDeliveryCity(),
                order.getDeliveryPincode(),
                order.getUserEmail());
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value.trim());
        }
    }

    private static String join(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + " " + right;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Quotes a CSV field only when it has to be quoted. */
    private static String csv(String value) {
        if (value == null || value.isEmpty()) return "";
        String v = value.replace("\r", " ").replace("\n", " ");
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /** Used by the renderer and the CSV header for a consistent money format. */
    public static String rupees(long paise) {
        return String.format(Locale.ROOT, "%,.2f", Money.toRupees(paise));
    }
}
