package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.model.InvoiceCounter;
import com.kamaldairy.kamal_dairy_backend.repository.InvoiceCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens the counter row for a new financial year, in a transaction of its own.
 *
 * This exists as a separate bean on purpose. Spring applies @Transactional
 * through a proxy, so a call from inside InvoiceNumberService to one of its own
 * methods would run with the caller's transaction and REQUIRES_NEW would be
 * silently ignored - which is exactly the case that matters here: when two
 * requests race to create the row, the loser's insert has to roll back alone,
 * without marking the order's transaction rollback-only.
 */
@Service
public class InvoiceCounterSeeder {

    private final InvoiceCounterRepository counters;

    public InvoiceCounterSeeder(InvoiceCounterRepository counters) {
        this.counters = counters;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void open(String financialYear) {
        counters.saveAndFlush(new InvoiceCounter(financialYear));
    }
}
