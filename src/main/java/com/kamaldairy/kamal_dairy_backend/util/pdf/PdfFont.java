package com.kamaldairy.kamal_dairy_backend.util.pdf;

/**
 * The two standard PDF fonts the invoice uses, with their real Adobe glyph
 * widths baked in.
 *
 * Every PDF viewer already has Helvetica, so nothing has to be embedded and an
 * invoice stays a few kilobytes. But the writer still has to know how wide a
 * string is - without that there is no right-aligned money column, no centred
 * heading and no line wrapping. Widths are in 1/1000 em, indexed by
 * WinAnsiEncoding code point from 32 (space) to 255, taken from the Adobe font
 * metrics for Helvetica and Helvetica-Bold.
 *
 * A code with width 0 is a glyph these fonts do not have; PdfDoc renders it as
 * a question mark rather than a blank, so text never silently loses characters.
 */
public enum PdfFont {

    REGULAR("Helvetica", "F1", new short[] {
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, 0,
        0, 0, 222, 556, 333, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
        0, 222, 222, 333, 333, 350, 556, 1000, 333, 1000, 500, 333, 944, 0, 500, 667,
        278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556, 584, 333, 737, 333,
        400, 584, 0, 0, 333, 556, 537, 278, 333, 0, 365, 556, 834, 834, 834, 611,
        667, 667, 667, 667, 667, 667, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
        722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
        556, 556, 556, 556, 556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 584, 611, 556, 556, 556, 556, 500, 556, 500
    }),

    BOLD("Helvetica-Bold", "F2", new short[] {
        278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
        975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
        333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
        611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584, 0,
        0, 0, 278, 556, 500, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
        0, 278, 278, 500, 500, 350, 556, 1000, 333, 1000, 556, 333, 944, 0, 500, 667,
        278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556, 584, 333, 737, 333,
        400, 584, 0, 0, 333, 611, 556, 278, 333, 0, 365, 556, 834, 834, 834, 611,
        722, 722, 722, 722, 722, 722, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
        722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
        556, 556, 556, 556, 556, 556, 889, 556, 556, 556, 556, 556, 278, 278, 278, 278,
        611, 611, 611, 611, 611, 611, 611, 584, 611, 611, 611, 611, 611, 556, 611, 556
    });

    static final int FIRST_CODE = 32;
    static final int LAST_CODE = 255;
    static final int FALLBACK = '?';

    private final String baseFont;
    private final String resourceName;
    private final short[] widths;

    PdfFont(String baseFont, String resourceName, short[] widths) {
        this.baseFont = baseFont;
        this.resourceName = resourceName;
        this.widths = widths;
    }

    String baseFont() { return baseFont; }

    String resourceName() { return resourceName; }

    /** Width of one WinAnsi code in 1/1000 em, or 0 when the font has no such glyph. */
    int rawWidth(int code) {
        if (code < FIRST_CODE || code > LAST_CODE) return 0;
        return widths[code - FIRST_CODE];
    }

    /** Width of one code after the question-mark substitution PdfDoc applies. */
    int width(int code) {
        int w = rawWidth(code);
        return w > 0 ? w : widths[FALLBACK - FIRST_CODE];
    }

    /** Width of a whole string at a point size. */
    public float width(String text, float size) {
        if (text == null || text.isEmpty()) return 0f;
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += width(text.charAt(i));
        }
        return total * size / 1000f;
    }
}
