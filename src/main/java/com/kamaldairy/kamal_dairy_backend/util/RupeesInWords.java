package com.kamaldairy.kamal_dairy_backend.util;

/**
 * Amount in words, Indian numbering system, for the line every invoice has to
 * carry: "Rupees One Thousand Two Hundred Fifty and Fifty Paise Only".
 *
 * Groups are crore / lakh / thousand / hundred - not millions - because that is
 * what an Indian buyer, auditor and accountant read.
 */
public final class RupeesInWords {

    private static final String[] UNITS = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private RupeesInWords() {}

    /** Paise to words, including the "Only" that closes an invoice amount. */
    public static String of(long paise) {
        if (paise < 0) {
            return "Minus " + of(-paise);
        }
        long rupees = paise / 100;
        int fraction = (int) (paise % 100);

        StringBuilder sb = new StringBuilder("Rupees ");
        sb.append(rupees == 0 ? "Zero" : words(rupees));
        if (fraction > 0) {
            sb.append(" and ").append(words(fraction)).append(" Paise");
        }
        return sb.append(" Only").toString();
    }

    /** A whole number in Indian-grouped words. */
    public static String words(long n) {
        if (n == 0) return "Zero";

        StringBuilder sb = new StringBuilder();
        append(sb, n / 10_000_000, "Crore");
        n %= 10_000_000;
        append(sb, n / 100_000, "Lakh");
        n %= 100_000;
        append(sb, n / 1_000, "Thousand");
        n %= 1_000;
        append(sb, n / 100, "Hundred");
        n %= 100;

        if (n > 0) {
            space(sb);
            sb.append(underHundred((int) n));
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, long count, String scale) {
        if (count == 0) return;
        space(sb);
        // A crore group can itself run into thousands, so it recurses.
        sb.append(count >= 100 ? words(count) : underHundred((int) count)).append(' ').append(scale);
    }

    private static String underHundred(int n) {
        if (n < 20) return UNITS[n];
        String tens = TENS[n / 10];
        return n % 10 == 0 ? tens : tens + " " + UNITS[n % 10];
    }

    private static void space(StringBuilder sb) {
        if (!sb.isEmpty()) sb.append(' ');
    }
}
