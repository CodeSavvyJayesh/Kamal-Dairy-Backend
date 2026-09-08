package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Conditional update = compare-and-set at the database level.
     * Only a row still in CREATED is moved to PAID, so two concurrent
     * "place order" calls carrying the same payment can never both win.
     * Returns the number of rows changed: 1 = we won, 0 = already consumed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PaymentOrder p " +
           "set p.status = 'PAID', p.razorpayPaymentId = :paymentId, p.paidAt = :paidAt " +
           "where p.id = :id and p.status = 'CREATED'")
    int markPaid(@Param("id") Long id,
                 @Param("paymentId") String paymentId,
                 @Param("paidAt") LocalDateTime paidAt);
}
