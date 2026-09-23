package com.kamaldairy.kamal_dairy_backend.util.pdf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A very small PDF writer: enough to lay out an invoice, and nothing else.
 *
 * Why hand-written instead of a library? An invoice is text, rules and boxes on
 * A4. Every PDF reader already carries Helvetica, so the only hard parts are
 * glyph widths (see {@link PdfFont}) and byte-exact cross-reference offsets.
 * That is a few hundred lines. Pulling in iText or PDFBox would add a
 * multi-megabyte dependency and, in iText's case, an AGPL licence, to draw
 * twenty lines of text. This writer produces a valid PDF 1.4 file with no
 * dependency at all and no licence to think about.
 *
 * Coordinates are PDF points with the origin at the BOTTOM-left of the page, so
 * y grows upwards. Text is positioned by its baseline. Everything is written in
 * Latin-1, which is byte-for-byte WinAnsiEncoding over the range the fonts
 * cover, so one character is always one byte and offsets stay predictable.
 *
 * Not supported, because the invoice does not need it: compression, embedded
 * fonts, images, transparency, annotations.
 */
public final class PdfDoc {

    /** A4 in points. */
    public static final float A4_WIDTH = 595.28f;
    public static final float A4_HEIGHT = 841.89f;

    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;
    private String title = "Document";

    public PdfDoc() {
        newPage();
    }

    public PdfDoc title(String title) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        return this;
    }

    public PdfDoc newPage() {
        page = new StringBuilder(4096);
        pages.add(page);
        return this;
    }

    public int pageCount() {
        return pages.size();
    }

    /**
     * Draws on an earlier page again. Needed for "Page 1 of 3": the total is
     * only known once the last page exists, so the footers are stamped at the
     * end rather than guessed at the start.
     */
    public PdfDoc selectPage(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IndexOutOfBoundsException("No page " + index);
        }
        page = pages.get(index);
        return this;
    }

    // ------------------------------------------------------------------ text

    /** Draws text with its left edge at x and its baseline at y. */
    public PdfDoc text(PdfFont font, float size, float x, float y, String s) {
        return text(font, size, x, y, s, Gray.BLACK);
    }

    public PdfDoc text(PdfFont font, float size, float x, float y, String s, Rgb colour) {
        if (s == null || s.isEmpty()) return this;
        page.append("BT\n")
            .append(colour.fill())
            .append('/').append(font.resourceName()).append(' ').append(num(size)).append(" Tf\n")
            .append(num(x)).append(' ').append(num(y)).append(" Td\n")
            .append('(').append(escape(s)).append(") Tj\n")
            .append("ET\n");
        return this;
    }

    /** Draws text with its right edge at x. Used for every money column. */
    public PdfDoc textRight(PdfFont font, float size, float x, float y, String s) {
        return textRight(font, size, x, y, s, Gray.BLACK);
    }

    public PdfDoc textRight(PdfFont font, float size, float x, float y, String s, Rgb colour) {
        if (s == null || s.isEmpty()) return this;
        return text(font, size, x - font.width(s, size), y, s, colour);
    }

    public PdfDoc textCentre(PdfFont font, float size, float centreX, float y, String s) {
        return textCentre(font, size, centreX, y, s, Gray.BLACK);
    }

    public PdfDoc textCentre(PdfFont font, float size, float centreX, float y, String s, Rgb colour) {
        if (s == null || s.isEmpty()) return this;
        return text(font, size, centreX - font.width(s, size) / 2f, y, s, colour);
    }

    /**
     * Draws text wrapped to a maximum width and returns the baseline y of the
     * last line drawn. Breaks on spaces, and hard-breaks a single word that is
     * longer than the column so a long product name can never spill over the
     * rule beside it.
     */
    public float textWrapped(PdfFont font, float size, float x, float y, float maxWidth,
                             float leading, String s) {
        return textWrapped(font, size, x, y, maxWidth, leading, s, Gray.BLACK, Integer.MAX_VALUE);
    }

    public float textWrapped(PdfFont font, float size, float x, float y, float maxWidth,
                             float leading, String s, Rgb colour, int maxLines) {
        List<String> lines = wrap(font, size, maxWidth, s, maxLines);
        float cursor = y;
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) cursor -= leading;
            text(font, size, x, cursor, lines.get(i), colour);
        }
        return cursor;
    }

    /** Splits text into lines that each fit maxWidth. Never returns more than maxLines. */
    public static List<String> wrap(PdfFont font, float size, float maxWidth, String s, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (s == null || s.isBlank() || maxLines <= 0) return lines;

        StringBuilder current = new StringBuilder();
        for (String word : s.trim().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate, size) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
                if (lines.size() == maxLines) return ellipsise(lines, font, size, maxWidth);
            }
            // A single word too wide for the column: break it character by character.
            while (font.width(word, size) > maxWidth && word.length() > 1) {
                int cut = 1;
                while (cut < word.length() && font.width(word.substring(0, cut + 1), size) <= maxWidth) {
                    cut++;
                }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
                if (lines.size() == maxLines) return ellipsise(lines, font, size, maxWidth);
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** Marks a clipped block so the reader can see something was cut. */
    private static List<String> ellipsise(List<String> lines, PdfFont font, float size, float maxWidth) {
        int last = lines.size() - 1;
        String line = lines.get(last);
        while (!line.isEmpty() && font.width(line + "...", size) > maxWidth) {
            line = line.substring(0, line.length() - 1);
        }
        lines.set(last, line + "...");
        return lines;
    }

    // --------------------------------------------------------------- shapes

    /** Horizontal or diagonal hairline. */
    public PdfDoc line(float x1, float y1, float x2, float y2, float width, Rgb colour) {
        page.append(colour.stroke())
            .append(num(width)).append(" w\n")
            .append(num(x1)).append(' ').append(num(y1)).append(" m ")
            .append(num(x2)).append(' ').append(num(y2)).append(" l S\n");
        return this;
    }

    public PdfDoc hline(float x1, float x2, float y, float width, Rgb colour) {
        return line(x1, y, x2, y, width, colour);
    }

    /** Filled rectangle, x/y at the bottom-left corner. */
    public PdfDoc fill(float x, float y, float w, float h, Rgb colour) {
        page.append(colour.fill())
            .append(num(x)).append(' ').append(num(y)).append(' ')
            .append(num(w)).append(' ').append(num(h)).append(" re f\n");
        return this;
    }

    /** Outlined rectangle. */
    public PdfDoc stroke(float x, float y, float w, float h, float width, Rgb colour) {
        page.append(colour.stroke())
            .append(num(width)).append(" w\n")
            .append(num(x)).append(' ').append(num(y)).append(' ')
            .append(num(w)).append(' ').append(num(h)).append(" re S\n");
        return this;
    }

    // ---------------------------------------------------------------- output

    /**
     * Serialises the document. Object offsets are counted in bytes as they are
     * written, because the cross-reference table at the end has to point at
     * them exactly or no reader will open the file.
     */
    public byte[] toBytes() {
        // 1 catalog, 2 pages, 3 regular font, 4 bold font, 5 info,
        // then one page object and one content stream per page.
        int firstPageObj = 6;
        int objectCount = 5 + pages.size() * 2;

        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        int[] offsets = new int[objectCount + 1];

        write(out, "%PDF-1.4\n");
        // A binary comment tells tools the file is not plain text.
        out.write(new byte[] {'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'}, 0, 6);

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(firstPageObj + i * 2).append(" 0 R ");
        }

        offsets[1] = out.size();
        write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets[2] = out.size();
        write(out, "2 0 obj\n<< /Type /Pages /Count " + pages.size()
                + " /Kids [" + kids.toString().trim() + "] >>\nendobj\n");

        offsets[3] = out.size();
        write(out, fontObject(3, PdfFont.REGULAR));

        offsets[4] = out.size();
        write(out, fontObject(4, PdfFont.BOLD));

        offsets[5] = out.size();
        write(out, "5 0 obj\n<< /Title (" + escape(title) + ") /Producer (Kamal Dairy) >>\nendobj\n");

        for (int i = 0; i < pages.size(); i++) {
            int pageObj = firstPageObj + i * 2;
            int streamObj = pageObj + 1;
            String content = pages.get(i).toString();

            offsets[pageObj] = out.size();
            write(out, pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R"
                    + " /MediaBox [0 0 " + num(A4_WIDTH) + ' ' + num(A4_HEIGHT) + "]"
                    + " /Resources << /Font << /" + PdfFont.REGULAR.resourceName() + " 3 0 R /"
                    + PdfFont.BOLD.resourceName() + " 4 0 R >> >>"
                    + " /Contents " + streamObj + " 0 R >>\nendobj\n");

            offsets[streamObj] = out.size();
            write(out, streamObj + " 0 obj\n<< /Length "
                    + content.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n");
            write(out, content);
            write(out, "endstream\nendobj\n");
        }

        int xref = out.size();
        StringBuilder table = new StringBuilder();
        table.append("xref\n0 ").append(objectCount + 1).append('\n');
        table.append("0000000000 65535 f \n");
        for (int i = 1; i <= objectCount; i++) {
            table.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        }
        table.append("trailer\n<< /Size ").append(objectCount + 1)
             .append(" /Root 1 0 R /Info 5 0 R >>\nstartxref\n")
             .append(xref).append("\n%%EOF\n");
        write(out, table.toString());

        return out.toByteArray();
    }

    private static String fontObject(int id, PdfFont font) {
        return id + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /" + font.baseFont()
                + " /Encoding /WinAnsiEncoding >>\nendobj\n";
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }

    /**
     * Escapes a string for a PDF literal and drops anything the font cannot
     * draw. Backslash, brackets and control characters would otherwise end the
     * literal early and corrupt the file.
     */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '(' || c == ')') {
                sb.append('\\').append(c);
            } else if (c >= PdfFont.FIRST_CODE && c <= PdfFont.LAST_CODE
                    && PdfFont.REGULAR.rawWidth(c) > 0) {
                sb.append(c);
            } else {
                sb.append((char) PdfFont.FALLBACK);
            }
        }
        return sb.toString();
    }

    /** Short, locale-independent number. PDF does not accept 1,5 or 1e3. */
    static String num(float v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e7) {
            return Integer.toString((int) Math.rint(v));
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // --------------------------------------------------------------- colours

    /** A colour in the PDF's default RGB space. */
    public record Rgb(float r, float g, float b) {

        public static Rgb of(int hex) {
            return new Rgb(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f);
        }

        String fill() {
            return num(r) + ' ' + num(g) + ' ' + num(b) + " rg\n";
        }

        String stroke() {
            return num(r) + ' ' + num(g) + ' ' + num(b) + " RG\n";
        }

        private static String num(float v) {
            return String.format(Locale.ROOT, "%.3f", v);
        }
    }

    /** The greys the invoice uses, so callers do not repeat magic numbers. */
    public static final class Gray {
        public static final Rgb BLACK = new Rgb(0f, 0f, 0f);
        public static final Rgb INK = Rgb.of(0x1A1A1A);
        public static final Rgb MUTED = Rgb.of(0x6B6B6B);
        public static final Rgb LINE = Rgb.of(0xD4D4D4);
        public static final Rgb HAIRLINE = Rgb.of(0xE8E8E8);
        public static final Rgb BAND = Rgb.of(0xF4F1EA);
        public static final Rgb WHITE = new Rgb(1f, 1f, 1f);

        private Gray() {}
    }
}
