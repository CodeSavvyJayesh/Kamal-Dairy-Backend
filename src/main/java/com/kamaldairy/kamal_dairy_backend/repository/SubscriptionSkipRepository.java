package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.SubscriptionSkip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SubscriptionSkipRepository extends JpaRepository<SubscriptionSkip, Long> {

    boolean existsBySubscriptionIdAndSkipDate(Long subscriptionId, LocalDate skipDate);

    List<SubscriptionSkip> findBySubscriptionIdAndSkipDateGreaterThanEqualOrderBySkipDateAsc(
            Long subscriptionId, LocalDate from);

    long deleteBySubscriptionIdAndSkipDate(Long subscriptionId, LocalDate skipDate);
}
