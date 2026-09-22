package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.SubscriptionGenerationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SubscriptionGenerationRunRepository extends JpaRepository<SubscriptionGenerationRun, Long> {

    boolean existsByDeliveryDate(LocalDate deliveryDate);

    Optional<SubscriptionGenerationRun> findByDeliveryDate(LocalDate deliveryDate);
}
