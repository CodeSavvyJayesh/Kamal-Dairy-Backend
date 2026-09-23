package com.kamaldairy.kamal_dairy_backend.util;

import java.time.LocalDate;

/**
 * The Indian financial year: 1 April to 31 March, labelled "2026-27".
 *
 * Invoice serials restart every financial year, so this label is the key the
 * invoice counter is kept under and the middle segment of every invoice number.
 */
public final class FinancialYear {

    private FinancialYear() {}

    /** "2026-27" for any date from 1 Apr 2026 to 31 Mar 2027. */
    public static String label(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }

    public static LocalDate startOf(String label) {
        return LocalDate.of(Integer.parseInt(label.substring(0, 4)), 4, 1);
    }
}
