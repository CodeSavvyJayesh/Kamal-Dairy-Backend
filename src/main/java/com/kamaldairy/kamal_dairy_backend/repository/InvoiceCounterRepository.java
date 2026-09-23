package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.InvoiceCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, String> {

    /**
     * Claims the next serial for a financial year. Returns 1 when the row
     * existed and was bumped, 0 when there is no row for that year yet.
     *
     * The UPDATE takes the row's write lock and keeps it until the surrounding
     * transaction commits, so a second allocation running at the same moment
     * blocks here instead of reading a stale value.
     *
     * flushAutomatically pushes pending changes to the database first, so the
     * order's new status is written before we take the lock. clearAutomatically
     * is deliberately NOT set: it would detach every entity in the session,
     * including the Order the caller is about to stamp the invoice number onto,
     * and that stamp would then be silently dropped at commit. The read below is
     * a scalar projection rather than an entity, so it goes to the database and
     * sees the incremented value without needing the session cleared.
     */
    @Modifying(flushAutomatically = true)
    @Query("update InvoiceCounter c set c.lastNumber = c.lastNumber + 1 where c.financialYear = :fy")
    int bump(@Param("fy") String financialYear);

    @Query("select c.lastNumber from InvoiceCounter c where c.financialYear = :fy")
    Integer currentNumber(@Param("fy") String financialYear);
}
