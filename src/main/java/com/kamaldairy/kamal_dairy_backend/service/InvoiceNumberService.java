package com.kamaldairy.kamal_dairy_backend.service;

import com.kamaldairy.kamal_dairy_backend.config.InvoiceProperties;
import com.kamaldairy.kamal_dairy_backend.exception.ApiException;
import com.kamaldairy.kamal_dairy_backend.repository.InvoiceCounterRepository;
import com.kamaldairy.kamal_dairy_backend.util.FinancialYear;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Hands out invoice numbers: KD/2026-27/000042.
 *
 * Consecutive within a financial year, unique, and never reused - the three
 * things GST asks of an invoice serial. Allocation joins the caller's
 * transaction, so a number is only spent if the order it belongs to is actually
 * saved; a rollback takes the serial back with it and nothing is skipped.
 */
@Service
public class InvoiceNumberService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNumberService.class);

    private static final int MAX_ATTEMPTS = 3;

    private final InvoiceCounterRepository counters;
    private final InvoiceCounterSeeder seeder;
    private final InvoiceProperties properties;

    public InvoiceNumberService(InvoiceCounterRepository counters,
                               InvoiceCounterSeeder seeder,
                               InvoiceProperties properties) {
        this.counters = counters;
        this.seeder = seeder;
        this.properties = properties;
    }

    /**
     * The next number for the financial year that contains {@code on}. Must be
     * called inside a transaction, so the number and the order commit together.
     *
     * The first invoice of a new financial year has no counter row yet, so one
     * is opened in a separate transaction and the bump retried. If two requests
     * race to open it, one wins and the loser's insert rolls back on its own -
     * the retry then bumps the row the winner created.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate(LocalDate on) {
        String financialYear = FinancialYear.label(on);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (counters.bump(financialYear) == 1) {
                Integer serial = counters.currentNumber(financialYear);
                if (serial != null) {
                    return format(financialYear, serial);
                }
            } else {
                open(financialYear);
            }
        }
        throw new ApiException("Could not allocate an invoice number. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void open(String financialYear) {
        try {
            seeder.open(financialYear);
            log.info("Opened invoice counter for financial year {}", financialYear);
        } catch (DataIntegrityViolationException | UnexpectedRollbackException e) {
            // Another request opened it first. That is the outcome we wanted.
            log.debug("Invoice counter for {} already existed", financialYear);
        }
    }

    public String format(String financialYear, int serial) {
        return properties.getPrefix() + "/" + financialYear + "/" + String.format("%06d", serial);
    }
}
