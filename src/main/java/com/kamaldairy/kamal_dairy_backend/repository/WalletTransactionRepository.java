package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByUserEmailOrderByCreatedAtDescIdDesc(String userEmail, Pageable pageable);

    List<WalletTransaction> findTop8ByUserEmailOrderByCreatedAtDescIdDesc(String userEmail);
}
