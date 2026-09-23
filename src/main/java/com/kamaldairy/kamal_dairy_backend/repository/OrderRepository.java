package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.Order;
import com.kamaldairy.kamal_dairy_backend.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order,Integer> {
    List<Order> findByUserEmail(String userEmail);

    /** Every status change goes through this row lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findForUpdate(@Param("id") Integer id);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByStatusIn(Collection<OrderStatus> statuses, Pageable pageable);

    /** Delivered, plus orders from before the lifecycle existed (no status). */
    Page<Order> findByStatusOrStatusIsNull(OrderStatus status, Pageable pageable);

    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countByStatus();

    /** Reviews: has this customer received this product in a cart order? (No status = older, delivered.) */
    @Query("select count(o) from Order o join o.items i where o.userEmail = :email and i.productId = :pid "
            + "and (o.status = :delivered or o.status is null)")
    long countReceived(@Param("email") String email, @Param("pid") Integer productId,
                       @Param("delivered") OrderStatus delivered);

    /** Analytics: orders placed in [from, to), items included. */
    @Query("select distinct o from Order o left join fetch o.items where o.createdAt >= :from and o.createdAt < :to")
    List<Order> findWithItemsPlacedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Invoice register for the accountant: everything issued in [from, to), items included. */
    @Query("select distinct o from Order o left join fetch o.items "
            + "where o.invoicedAt >= :from and o.invoicedAt < :to order by o.invoiceNo")
    List<Order> findInvoicedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Analytics: each customer's first order that was not cancelled. */
    @Query("select o.userEmail, min(o.createdAt) from Order o where o.status is null or o.status <> :cancelled group by o.userEmail")
    List<Object[]> firstOrderPerCustomer(@Param("cancelled") OrderStatus cancelled);
}