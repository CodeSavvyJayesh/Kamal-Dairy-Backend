package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per financial year, holding the last invoice serial issued in it.
 *
 * GST requires invoice numbers to be consecutive and unique within a financial
 * year, which rules out deriving them from the order id: order ids skip
 * cancelled orders and run across year boundaries. A counter row does the job,
 * and because it is incremented with a single UPDATE the row lock it takes
 * serialises every concurrent allocation - two orders delivered in the same
 * millisecond queue up rather than both reading the same number.
 */
@Entity
@Table(name = "invoice_counters")
public class InvoiceCounter {

    /** Financial year label, e.g. "2026-27". */
    @Id
    @Column(name = "financial_year", length = 9, nullable = false)
    private String financialYear;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    public InvoiceCounter() {}

    public InvoiceCounter(String financialYear) {
        this.financialYear = financialYear;
        this.lastNumber = 0;
    }

    public String getFinancialYear() { return financialYear; }

    public int getLastNumber() { return lastNumber; }

    public void setLastNumber(int lastNumber) { this.lastNumber = lastNumber; }
}
