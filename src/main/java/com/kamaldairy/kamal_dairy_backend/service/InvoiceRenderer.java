package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.config.InvoiceProperties;
import com.kamaldairy.kamal_dairy_backend.dto.InvoiceView;
import com.kamaldairy.kamal_dairy_backend.util.pdf.PdfDoc;
import com.kamaldairy.kamal_dairy_backend.util.pdf.PdfFont;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Draws an {@link InvoiceView} onto A4.
 *
 * Layout only - every figure arrives already computed, so nothing on the page
 * can disagree with what the service worked out. Long item lists flow onto
 * further pages with the table header repeated, and the page footers are stamped
 * at the end once the total page count is known.
 *
 * Amounts are labelled "Rs." rather than the rupee sign: the standard PDF fonts
 * are WinAnsi-encoded and have no rupee glyph, and a missing glyph on an invoice
 * is worse than three honest letters.
 */
@Component
public class InvoiceRenderer {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);

    private static final float MARGIN = 36f;
    private static final float LEFT = MARGIN;
    private static final float RIGHT = PdfDoc.A4_WIDTH - MARGIN;
    private static final float WIDTH = RIGHT - LEFT;

    /** Nothing in the body is drawn below this; the footer owns the space under it. */
    private static final float MIN_Y = 124f;

    private static final float ROW_H = 17f;
    private static final float PAD = 6f;

    private final InvoiceProperties properties;

    public InvoiceRenderer(InvoiceProperties properties) {
        this.properties = properties;
    }

    public byte[] render(InvoiceView v) {
        PdfDoc doc = new PdfDoc().title(
                (v.number() == null ? v.kind().label() : v.number()) + " - Kamal Dairy");

        float y = header(doc, v, false);
        y = meta(doc, v, y - 14f);
        y = parties(doc, v, y - 14f);
        y = items(doc, v, y - 16f);

        if (v.taxable() && !v.taxSummary().isEmpty()) {
            y = taxSummary(doc, v, y - 16f);
        }
        y = closing(doc, v, y - 16f);
        footers(doc, v);

        return doc.toBytes();
    }

    // --------------------------------------------------------------- header

    /** Returns the y below the header band. */
    private float header(PdfDoc doc, InvoiceView v, boolean continued) {
        InvoiceView.Seller s = v.seller();

        List<String> left = new java.util.ArrayList<>(s.addressLines());
        String contact = joinNonBlank(" · ", s.phone(), s.email());
        if (!contact.isEmpty()) {
            left.add(contact);
        }

        boolean hasLegalLine = !properties.getSellerName().equalsIgnoreCase(properties.displayName());

        // Tall enough for the last address line plus its descender: the band is
        // sized from the text rather than guessed, so a dairy with a long address
        // never has its email printed across the border.
        float bandTop = PdfDoc.A4_HEIGHT - MARGIN;
        float bandHeight = continued
                ? 44f
                : Math.max(78f, 43f + (hasLegalLine ? 10.5f : 0f) + Math.max(0, left.size() - 1) * 10.5f);
        float bandBottom = bandTop - bandHeight;

        doc.fill(LEFT, bandBottom, WIDTH, bandHeight, PdfDoc.Gray.BAND);
        doc.hline(LEFT, RIGHT, bandBottom, 1.2f, PdfDoc.Gray.LINE);

        float x = LEFT + 14f;
        float cursor = bandTop - 24f;
        doc.text(PdfFont.BOLD, continued ? 13f : 19f, x, cursor, properties.displayName(), PdfDoc.Gray.INK);

        if (continued) {
            doc.textRight(PdfFont.REGULAR, 9f, RIGHT - 14f, cursor,
                    v.kind().label() + (v.number() == null ? "" : " " + v.number()) + " (continued)",
                    PdfDoc.Gray.MUTED);
            return bandBottom;
        }

        cursor -= 13f;
        if (hasLegalLine) {
            doc.text(PdfFont.REGULAR, 8f, x, cursor, properties.getSellerName(), PdfDoc.Gray.MUTED);
            cursor -= 10.5f;
        }
        for (String line : left) {
            doc.text(PdfFont.REGULAR, 8.5f, x, cursor, line, PdfDoc.Gray.MUTED);
            cursor -= 10.5f;
        }

        // Right side: what this document is, and the seller's registrations.
        float rx = RIGHT - 14f;
        doc.textRight(PdfFont.BOLD, 15f, rx, bandTop - 24f,
                v.kind().label().toUpperCase(Locale.ENGLISH), PdfDoc.Gray.INK);

        float ry = bandTop - 40f;
        if (s.gstin() != null) {
            doc.textRight(PdfFont.REGULAR, 8.5f, rx, ry, "GSTIN " + s.gstin(), PdfDoc.Gray.MUTED);
            ry -= 10.5f;
        }
        if (s.fssai() != null) {
            doc.textRight(PdfFont.REGULAR, 8.5f, rx, ry, "FSSAI " + s.fssai(), PdfDoc.Gray.MUTED);
            ry -= 10.5f;
        }
        if (v.kind() == InvoiceView.Kind.PROFORMA) {
            doc.textRight(PdfFont.BOLD, 8f, rx, ry, "Not a tax invoice", PdfDoc.Gray.MUTED);
        }

        return bandBottom;
    }

    // ----------------------------------------------------------------- meta

    /** Invoice number, dates, order reference and how it was paid. */
    private float meta(PdfDoc doc, InvoiceView v, float top) {
        String[][] cells = {
                {v.kind() == InvoiceView.Kind.PROFORMA ? "Proforma for order" : "Invoice no.",
                 v.number() == null ? "#" + v.orderId() : v.number()},
                {v.kind() == InvoiceView.Kind.PROFORMA ? "Date" : "Invoice date",
                 v.issuedOn() == null ? "-" : DAY.format(v.issuedOn())},
                {"Order no.", "#" + v.orderId()},
                {"Order placed", v.orderedAt() == null ? "-" : DAY_TIME.format(v.orderedAt())},
                {"Paid by", v.paymentMethod()},
                {v.paymentReference() == null ? "Order status" : "Payment ref.",
                 v.paymentReference() == null ? v.orderStatus() : v.paymentReference()},
        };

        float rows = 3f;
        float height = rows * ROW_H;
        float bottom = top - height;
        float colWidth = WIDTH / 2f;

        doc.stroke(LEFT, bottom, WIDTH, height, 0.7f, PdfDoc.Gray.LINE);
        doc.line(LEFT + colWidth, bottom, LEFT + colWidth, top, 0.7f, PdfDoc.Gray.LINE);
        for (int r = 1; r < rows; r++) {
            doc.hline(LEFT, RIGHT, top - r * ROW_H, 0.5f, PdfDoc.Gray.HAIRLINE);
        }

        for (int i = 0; i < cells.length; i++) {
            float cx = LEFT + (i % 2) * colWidth;
            float cy = top - (i / 2) * ROW_H - 11.5f;
            doc.text(PdfFont.REGULAR, 8f, cx + PAD, cy, cells[i][0], PdfDoc.Gray.MUTED);
            doc.textRight(PdfFont.BOLD, 8.5f, cx + colWidth - PAD, cy, cells[i][1], PdfDoc.Gray.INK);
        }
        return bottom;
    }

    // --------------------------------------------------------------- parties

    /** Bill to on the left, ship to on the right. */
    private float parties(PdfDoc doc, InvoiceView v, float top) {
        InvoiceView.Buyer b = v.buyer();

        List<String> billed = List.of(
                nullToDash(b.name()),
                nullToDash(b.email()),
                b.phone() == null ? "" : "Phone " + b.phone());

        List<String> shipped = new java.util.ArrayList<>();
        shipped.add(nullToDash(b.name()));
        if (b.address() != null) shipped.add(b.address());
        String cityLine = joinNonBlank(" - ", b.city(), b.pincode());
        if (!cityLine.isEmpty()) shipped.add(cityLine);

        float colWidth = WIDTH / 2f;
        float innerWidth = colWidth - 2 * PAD - 6f;

        // Both columns wrap, so measure before drawing the boxes.
        int billedLines = countLines(billed, innerWidth);
        int shippedLines = countLines(shipped, innerWidth);
        float height = 20f + Math.max(billedLines, shippedLines) * 11.5f + 8f;
        float bottom = top - height;

        doc.stroke(LEFT, bottom, WIDTH, height, 0.7f, PdfDoc.Gray.LINE);
        doc.line(LEFT + colWidth, bottom, LEFT + colWidth, top, 0.7f, PdfDoc.Gray.LINE);
        doc.fill(LEFT + 0.7f, top - 16f, WIDTH - 1.4f, 15.3f, PdfDoc.Gray.BAND);
        doc.hline(LEFT, RIGHT, top - 16f, 0.5f, PdfDoc.Gray.LINE);

        doc.text(PdfFont.BOLD, 8f, LEFT + PAD + 3f, top - 11.5f, "BILL TO", PdfDoc.Gray.MUTED);
        doc.text(PdfFont.BOLD, 8f, LEFT + colWidth + PAD + 3f, top - 11.5f,
                "SHIP TO (DELIVERY ADDRESS)", PdfDoc.Gray.MUTED);

        block(doc, billed, LEFT + PAD + 3f, top - 28f, innerWidth);
        block(doc, shipped, LEFT + colWidth + PAD + 3f, top - 28f, innerWidth);

        return bottom;
    }

    private int countLines(List<String> values, float width) {
        int n = 0;
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            n += Math.max(1, PdfDoc.wrap(PdfFont.REGULAR, 8.5f, width, value, 3).size());
        }
        return Math.max(1, n);
    }

    private void block(PdfDoc doc, List<String> values, float x, float y, float width) {
        float cursor = y;
        boolean first = true;
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            PdfFont font = first ? PdfFont.BOLD : PdfFont.REGULAR;
            PdfDoc.Rgb colour = first ? PdfDoc.Gray.INK : PdfDoc.Gray.MUTED;
            cursor = doc.textWrapped(font, 8.5f, x, cursor, width, 11.5f, value, colour, 3);
            cursor -= 11.5f;
            first = false;
        }
    }

    // ---------------------------------------------------------------- items

    /** Column x positions, computed once per document from whether tax is shown. */
    private record Columns(float serial, float description, float descWidth,
                           float qty, float rate, float taxable, float gstRate, float gst, float amount) {

        static Columns of(boolean taxable) {
            float serialW = 20f, qtyW = 30f, rateW = 58f, amountW = 68f;
            if (!taxable) {
                float descW = WIDTH - serialW - qtyW - rateW - amountW - 4 * PAD;
                float x = LEFT;
                float serial = x + serialW;
                float desc = serial + PAD;
                float qty = desc + descW + PAD + qtyW;
                float rate = qty + PAD + rateW;
                float amount = RIGHT - PAD;
                return new Columns(serial, desc, descW, qty, rate, 0f, 0f, 0f, amount);
            }
            float taxableW = 62f, gstRateW = 32f, gstW = 54f;
            float descW = WIDTH - serialW - qtyW - rateW - taxableW - gstRateW - gstW - amountW - 7 * PAD;
            float serial = LEFT + serialW;
            float desc = serial + PAD;
            float qty = desc + descW + PAD + qtyW;
            float rate = qty + PAD + rateW;
            float tx = rate + PAD + taxableW;
            float gstRate = tx + PAD + gstRateW;
            float gst = gstRate + PAD + gstW;
            return new Columns(serial, desc, descW, qty, rate, tx, gstRate, gst, RIGHT - PAD);
        }
    }

    private float items(PdfDoc doc, InvoiceView v, float top) {
        Columns c = Columns.of(v.taxable());
        float y = tableHead(doc, v, c, top);

        for (InvoiceView.Line line : v.lines()) {
            List<String> nameLines = PdfDoc.wrap(PdfFont.REGULAR, 8.5f, c.descWidth(), line.description(), 2);
            boolean showHsn = v.taxable() && !line.hsn().isBlank();
            float rowHeight = Math.max(ROW_H, 4f + nameLines.size() * 11f + (showHsn ? 9.5f : 0f));

            if (y - rowHeight < MIN_Y + 150f) {
                // Not enough room for this row plus the totals: carry on overleaf.
                doc.newPage();
                float next = header(doc, v, true);
                y = tableHead(doc, v, c, next - 14f);
            }

            float textY = y - 12f;
            doc.textRight(PdfFont.REGULAR, 8.5f, c.serial(), textY,
                    Integer.toString(line.serial()), PdfDoc.Gray.MUTED);

            float nameY = textY;
            for (String part : nameLines) {
                doc.text(PdfFont.REGULAR, 8.5f, c.description(), nameY, part, PdfDoc.Gray.INK);
                nameY -= 11f;
            }
            if (showHsn) {
                doc.text(PdfFont.REGULAR, 7f, c.description(), nameY + 1f, "HSN " + line.hsn(),
                        PdfDoc.Gray.MUTED);
            }

            doc.textRight(PdfFont.REGULAR, 8.5f, c.qty(), textY, Integer.toString(line.quantity()));
            doc.textRight(PdfFont.REGULAR, 8.5f, c.rate(), textY, InvoiceService.rupees(line.unitPricePaise()));
            if (v.taxable()) {
                doc.textRight(PdfFont.REGULAR, 8.5f, c.taxable(), textY,
                        InvoiceService.rupees(line.taxablePaise()));
                doc.textRight(PdfFont.REGULAR, 8.5f, c.gstRate(), textY, line.gstRatePercent() + "%");
                doc.textRight(PdfFont.REGULAR, 8.5f, c.gst(), textY,
                        InvoiceService.rupees(line.cgstPaise() + line.sgstPaise()));
            }
            doc.textRight(PdfFont.BOLD, 8.5f, c.amount(), textY, InvoiceService.rupees(line.grossPaise()));

            y -= rowHeight;
            doc.hline(LEFT, RIGHT, y, 0.5f, PdfDoc.Gray.HAIRLINE);
        }

        if (v.lines().isEmpty()) {
            doc.text(PdfFont.REGULAR, 8.5f, c.description(), y - 12f, "No items on this order.",
                    PdfDoc.Gray.MUTED);
            y -= ROW_H;
        }
        return y;
    }

    private float tableHead(PdfDoc doc, InvoiceView v, Columns c, float top) {
        float bottom = top - ROW_H;
        doc.fill(LEFT, bottom, WIDTH, ROW_H, PdfDoc.Gray.BAND);
        doc.hline(LEFT, RIGHT, top, 0.7f, PdfDoc.Gray.LINE);
        doc.hline(LEFT, RIGHT, bottom, 0.7f, PdfDoc.Gray.LINE);

        float y = top - 11.5f;
        doc.textRight(PdfFont.BOLD, 7.5f, c.serial(), y, "#", PdfDoc.Gray.MUTED);
        doc.text(PdfFont.BOLD, 7.5f, c.description(), y, "ITEM", PdfDoc.Gray.MUTED);
        doc.textRight(PdfFont.BOLD, 7.5f, c.qty(), y, "QTY", PdfDoc.Gray.MUTED);
        doc.textRight(PdfFont.BOLD, 7.5f, c.rate(), y, "RATE", PdfDoc.Gray.MUTED);
        if (v.taxable()) {
            doc.textRight(PdfFont.BOLD, 7.5f, c.taxable(), y, "TAXABLE", PdfDoc.Gray.MUTED);
            doc.textRight(PdfFont.BOLD, 7.5f, c.gstRate(), y, "GST", PdfDoc.Gray.MUTED);
            doc.textRight(PdfFont.BOLD, 7.5f, c.gst(), y, "GST AMT", PdfDoc.Gray.MUTED);
        }
        doc.textRight(PdfFont.BOLD, 7.5f, c.amount(), y, "AMOUNT", PdfDoc.Gray.MUTED);
        return bottom;
    }

    // ----------------------------------------------------------- tax summary

    /** Tax grouped by rate: the shape a GSTR return wants. */
    private float taxSummary(PdfDoc doc, InvoiceView v, float top) {
        float tableWidth = 300f;
        float rows = 1f + v.taxSummary().size() + 1f;
        float height = rows * ROW_H;
        float bottom = top - height;

        float c1 = LEFT + 64f;          // rate, right aligned
        float c2 = LEFT + 146f;         // taxable
        float c3 = LEFT + 224f;         // cgst
        float c4 = LEFT + tableWidth;   // sgst

        doc.fill(LEFT, top - ROW_H, tableWidth, ROW_H, PdfDoc.Gray.BAND);
        doc.stroke(LEFT, bottom, tableWidth, height, 0.7f, PdfDoc.Gray.LINE);

        float y = top - 11.5f;
        doc.textRight(PdfFont.BOLD, 7.5f, c1, y, "GST RATE", PdfDoc.Gray.MUTED);
        doc.textRight(PdfFont.BOLD, 7.5f, c2, y, "TAXABLE", PdfDoc.Gray.MUTED);
        doc.textRight(PdfFont.BOLD, 7.5f, c3, y, "CGST", PdfDoc.Gray.MUTED);
        doc.textRight(PdfFont.BOLD, 7.5f, c4 - PAD, y, "SGST", PdfDoc.Gray.MUTED);

        float rowY = top - ROW_H;
        for (InvoiceView.TaxRow row : v.taxSummary()) {
            float ty = rowY - 11.5f;
            doc.textRight(PdfFont.REGULAR, 8.5f, c1, ty, row.gstRatePercent() + "%");
            doc.textRight(PdfFont.REGULAR, 8.5f, c2, ty, InvoiceService.rupees(row.taxablePaise()));
            String half = row.gstRatePercent() == 0 ? "" : " (" + half(row.gstRatePercent()) + ")";
            doc.textRight(PdfFont.REGULAR, 8.5f, c3, ty, InvoiceService.rupees(row.cgstPaise()) + half);
            doc.textRight(PdfFont.REGULAR, 8.5f, c4 - PAD, ty, InvoiceService.rupees(row.sgstPaise()) + half);
            rowY -= ROW_H;
            doc.hline(LEFT, LEFT + tableWidth, rowY, 0.5f, PdfDoc.Gray.HAIRLINE);
        }

        float ty = rowY - 11.5f;
        doc.textRight(PdfFont.BOLD, 8.5f, c1, ty, "Total", PdfDoc.Gray.INK);
        doc.textRight(PdfFont.BOLD, 8.5f, c2, ty, InvoiceService.rupees(v.taxableTotalPaise()));
        doc.textRight(PdfFont.BOLD, 8.5f, c3, ty, InvoiceService.rupees(v.cgstTotalPaise()));
        doc.textRight(PdfFont.BOLD, 8.5f, c4 - PAD, ty, InvoiceService.rupees(v.sgstTotalPaise()));

        return bottom;
    }

    /** "2.5%" for a 5% slab: CGST and SGST are each half the rate. */
    private static String half(int ratePercent) {
        return ratePercent % 2 == 0
                ? (ratePercent / 2) + "%"
                : String.format(Locale.ROOT, "%.1f%%", ratePercent / 2.0);
    }

    // --------------------------------------------------------------- closing

    /** Totals on the right, amount in words and the declaration on the left. */
    private float closing(PdfDoc doc, InvoiceView v, float top) {

        float boxWidth = 232f;
        float boxLeft = RIGHT - boxWidth;

        List<String[]> rows = new java.util.ArrayList<>();
        if (v.taxable()) {
            rows.add(new String[] {"Taxable value", InvoiceService.rupees(v.taxableTotalPaise())});
            rows.add(new String[] {"CGST", InvoiceService.rupees(v.cgstTotalPaise())});
            rows.add(new String[] {"SGST", InvoiceService.rupees(v.sgstTotalPaise())});
        } else {
            rows.add(new String[] {"Subtotal", InvoiceService.rupees(v.grandTotalPaise() - v.roundOffPaise())});
        }
        if (v.roundOffPaise() != 0) {
            rows.add(new String[] {"Rounding", InvoiceService.rupees(v.roundOffPaise())});
        }

        float totalRowH = 24f;
        float height = rows.size() * ROW_H + totalRowH;
        float bottom = top - height;

        doc.stroke(boxLeft, bottom, boxWidth, height, 0.7f, PdfDoc.Gray.LINE);

        float y = top;
        for (String[] row : rows) {
            doc.text(PdfFont.REGULAR, 8.5f, boxLeft + PAD + 2f, y - 11.5f, row[0], PdfDoc.Gray.MUTED);
            doc.textRight(PdfFont.REGULAR, 8.5f, RIGHT - PAD - 2f, y - 11.5f, row[1]);
            y -= ROW_H;
            doc.hline(boxLeft, RIGHT, y, 0.5f, PdfDoc.Gray.HAIRLINE);
        }

        doc.fill(boxLeft + 0.7f, y - totalRowH + 0.7f, boxWidth - 1.4f, totalRowH - 1.4f, PdfDoc.Gray.BAND);
        doc.text(PdfFont.BOLD, 9.5f, boxLeft + PAD + 2f, y - 16f,
                v.taxable() ? "Total (incl. GST)" : "Total", PdfDoc.Gray.INK);
        doc.textRight(PdfFont.BOLD, 11f, RIGHT - PAD - 2f, y - 16.5f,
                "Rs. " + InvoiceService.rupees(v.grandTotalPaise()), PdfDoc.Gray.INK);

        // Left column, beside the totals box.
        float leftWidth = boxLeft - LEFT - 16f;
        float cursor = top - 2f;
        doc.text(PdfFont.BOLD, 8f, LEFT, cursor, "AMOUNT IN WORDS", PdfDoc.Gray.MUTED);
        cursor -= 12f;
        cursor = doc.textWrapped(PdfFont.BOLD, 8.5f, LEFT, cursor, leftWidth, 11.5f,
                v.amountInWords(), PdfDoc.Gray.INK, 4);
        cursor -= 16f;

        if (v.taxable()) {
            cursor = doc.textWrapped(PdfFont.REGULAR, 7.5f, LEFT, cursor, leftWidth, 10f,
                    "Prices include GST. Tax has been worked out backwards from the shelf price, "
                    + "so the total above is exactly what was charged.",
                    PdfDoc.Gray.MUTED, 3);
            cursor -= 14f;
        } else if (v.kind() == InvoiceView.Kind.BILL_OF_SUPPLY) {
            cursor = doc.textWrapped(PdfFont.REGULAR, 7.5f, LEFT, cursor, leftWidth, 10f,
                    "No GST has been charged on this supply.", PdfDoc.Gray.MUTED, 2);
            cursor -= 14f;
        } else {
            cursor = doc.textWrapped(PdfFont.REGULAR, 7.5f, LEFT, cursor, leftWidth, 10f,
                    "This is a proforma for an order that has not been delivered yet. "
                    + "The tax invoice is issued on delivery.", PdfDoc.Gray.MUTED, 3);
            cursor -= 14f;
        }

        return Math.min(bottom, cursor);
    }

    // --------------------------------------------------------------- footers

    /** Declaration, signature and page numbers, stamped once every page exists. */
    private void footers(PdfDoc doc, InvoiceView v) {
        int pages = doc.pageCount();
        // The declaration column stops well short of the signature block, so the
        // two never collide however long the terms line is.
        float textWidth = WIDTH - 200f;

        for (int i = 0; i < pages; i++) {
            doc.selectPage(i);
            doc.hline(LEFT, RIGHT, MIN_Y - 6f, 0.7f, PdfDoc.Gray.LINE);

            if (i == pages - 1) {
                float after = doc.textWrapped(PdfFont.REGULAR, 7.5f, LEFT, MIN_Y - 18f, textWidth, 9.5f,
                        properties.getDeclaration(), PdfDoc.Gray.MUTED, 3);
                if (!properties.getTerms().isBlank()) {
                    doc.textWrapped(PdfFont.REGULAR, 7.5f, LEFT, after - 12f, textWidth, 9.5f,
                            properties.getTerms(), PdfDoc.Gray.MUTED, 2);
                }

                doc.textRight(PdfFont.BOLD, 8f, RIGHT, MIN_Y - 18f,
                        "For " + properties.displayName(), PdfDoc.Gray.INK);
                doc.hline(RIGHT - 150f, RIGHT, 56f, 0.5f, PdfDoc.Gray.LINE);
                doc.textRight(PdfFont.REGULAR, 7.5f, RIGHT, 46f,
                        properties.getSignatory().isBlank()
                                ? "Authorised signatory"
                                : properties.getSignatory() + " · Authorised signatory",
                        PdfDoc.Gray.MUTED);
            }

            doc.text(PdfFont.REGULAR, 7f, LEFT, 26f,
                    "Computer generated " + v.kind().label().toLowerCase(Locale.ENGLISH)
                    + " · Kamal Dairy · kamaldairy.online", PdfDoc.Gray.MUTED);
            doc.textRight(PdfFont.REGULAR, 7f, RIGHT, 26f,
                    "Page " + (i + 1) + " of " + pages, PdfDoc.Gray.MUTED);
        }
    }

    // --------------------------------------------------------------- helpers

    private static String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(separator);
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
