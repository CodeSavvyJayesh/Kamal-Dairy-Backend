package com.kamaldairy.kamal_dairy_backend.dto;

import com.kamaldairy.kamal_dairy_backend.util.Money;
import com.kamaldairy.kamal_dairy_backend.util.RupeesInWords;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything one invoice prints, already computed. Money is in paise: the
 * renderer only formats, it never does arithmetic, so the figures on the paper
 * are exactly the figures the service worked out and checked.
 */
public record InvoiceView(

        /** Kind of document, which depends on GST registration and order state. */
        Kind kind,

        /** Invoice number, or null on a proforma that has not been issued yet. */
        String number,

        LocalDate issuedOn,
        Integer orderId,
        LocalDateTime orderedAt,
        String orderStatus,

        /** RAZORPAY or WALLET, spelled for a human. */
        String paymentMethod,
        String paymentReference,

        Seller seller,
        Buyer buyer,

        List<Line> lines,

        /** One row per GST rate present, for the tax summary table. */
        List<TaxRow> taxSummary,

        long taxableTotalPaise,
        long cgstTotalPaise,
        long sgstTotalPaise,
        long grandTotalPaise,

        /** Non-zero only when the item lines and the order total disagree. */
        long roundOffPaise,

        boolean taxable
) {

    public enum Kind {
        TAX_INVOICE("Tax Invoice"),
        BILL_OF_SUPPLY("Bill of Supply"),
        PROFORMA("Proforma Invoice");

        private final String label;

        Kind(String label) { this.label = label; }

        public String label() { return label; }
    }

    public record Seller(String name, String gstin, String fssai, List<String> addressLines,
                         String phone, String email, String state) {}

    public record Buyer(String name, String phone, String address, String city, String pincode,
                        String email) {}

    /** One item line. Rate is the shelf price, which already includes any GST. */
    public record Line(int serial, String description, String hsn, int quantity,
                       long unitPricePaise, long grossPaise, int gstRatePercent,
                       long taxablePaise, long cgstPaise, long sgstPaise) {}

    /** Tax grouped by rate, which is how a GST return reads it. */
    public record TaxRow(int gstRatePercent, long taxablePaise, long cgstPaise, long sgstPaise) {

        public long taxPaise() { return cgstPaise + sgstPaise; }
    }

    public String amountInWords() {
        return RupeesInWords.of(grandTotalPaise);
    }

    public BigDecimal grandTotal() {
        return Money.toRupees(grandTotalPaise);
    }

    /** Filename the browser should save, e.g. "Kamal-Dairy-invoice-KD-2026-27-000042.pdf". */
    public String fileName() {
        String stem = number == null
                ? "order-" + orderId
                : number.replaceAll("[^A-Za-z0-9]+", "-");
        return "Kamal-Dairy-" + (kind == Kind.PROFORMA ? "proforma-" : "invoice-") + stem + ".pdf";
    }
}
