package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.Subscription;
import com.kamaldairy.kamal_dairy_backend.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    List<Subscription> findByUserEmailAndStatus(String userEmail, SubscriptionStatus status);

    /** Ownership is part of the lookup itself, so another customer's id simply is not found. */
    Optional<Subscription> findByIdAndUserEmail(Long id, String userEmail);

    List<Subscription> findByStatus(SubscriptionStatus status);

    long countByStatus(SubscriptionStatus status);

    long countByUserEmailAndStatusIn(String userEmail, Collection<SubscriptionStatus> statuses);

    @Query("select s.id from Subscription s where s.status = :status and s.startDate <= :date order by s.id")
    List<Long> findIdsStartedBy(@Param("status") SubscriptionStatus status, @Param("date") LocalDate date);
}
