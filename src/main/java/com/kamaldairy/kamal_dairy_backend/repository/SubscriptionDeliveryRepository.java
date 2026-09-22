package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.DeliveryStatus;
import com.kamaldairy.kamal_dairy_backend.model.SubscriptionDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionDeliveryRepository extends JpaRepository<SubscriptionDelivery, Long> {

    boolean existsBySubscriptionIdAndDeliveryDate(Long subscriptionId, LocalDate deliveryDate);

    /** Reviews: has this customer received this product through a subscription? */
    long countByUserEmailAndProductIdAndStatus(String userEmail, Integer productId, DeliveryStatus status);

    /** Analytics: every delivery in a date range, any status. */
    List<SubscriptionDelivery> findByDeliveryDateBetween(LocalDate from, LocalDate to);

    /** Analytics: each customer's first delivery that was charged. */
    @Query("select d.userEmail, min(d.deliveryDate) from SubscriptionDelivery d where d.status in :statuses group by d.userEmail")
    List<Object[]> firstChargedDeliveryPerCustomer(@Param("statuses") Collection<DeliveryStatus> statuses);

    List<SubscriptionDelivery> findByDeliveryDate(LocalDate deliveryDate);

    List<SubscriptionDelivery> findBySubscriptionIdAndDeliveryDateBetween(
            Long subscriptionId, LocalDate from, LocalDate to);

    List<SubscriptionDelivery> findBySubscriptionIdAndDeliveryDateGreaterThanEqualAndStatusOrderByDeliveryDateAsc(
            Long subscriptionId, LocalDate from, DeliveryStatus status);

    Page<SubscriptionDelivery> findByUserEmailOrderByDeliveryDateDescIdDesc(String userEmail, Pageable pageable);

    Page<SubscriptionDelivery> findByUserEmailAndSubscriptionIdOrderByDeliveryDateDescIdDesc(
            String userEmail, Long subscriptionId, Pageable pageable);

    int countByDeliveryDateAndStatusIn(LocalDate deliveryDate, Collection<DeliveryStatus> statuses);

    @Query("select coalesce(sum(d.amountPaise), 0) from SubscriptionDelivery d "
            + "where d.deliveryDate = :date and d.status in :statuses")
    long sumAmount(@Param("date") LocalDate date, @Param("statuses") Collection<DeliveryStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SubscriptionDelivery d where d.id = :id")
    Optional<SubscriptionDelivery> findForUpdate(@Param("id") Long id);

    /** Anything still SCHEDULED from a previous day was handed over - close it out. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SubscriptionDelivery d set d.status = :delivered, d.note = :note, d.updatedAt = :now "
            + "where d.status = :scheduled and d.deliveryDate < :today")
    int autoConfirmBefore(@Param("today") LocalDate today,
                          @Param("scheduled") DeliveryStatus scheduled,
                          @Param("delivered") DeliveryStatus delivered,
                          @Param("note") String note,
                          @Param("now") LocalDateTime now);
}
