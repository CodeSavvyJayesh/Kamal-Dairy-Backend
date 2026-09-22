package com.kamaldairy.kamal_dairy_backend.repository;

import com.kamaldairy.kamal_dairy_backend.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Balance changes are single conditional UPDATE statements, never
 * load-modify-save. The UPDATE takes an exclusive row lock that InnoDB holds
 * until the surrounding transaction commits, so concurrent debits serialise and
 * the "balance >= amount" check cannot be raced into a negative balance.
 *
 * clearAutomatically is deliberately false: callers (the delivery processor,
 * the wallet checkout) hold other managed entities in the same transaction and
 * must not have them detached underneath them.
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserEmail(String userEmail);

    boolean existsByUserEmail(String userEmail);

    /** Race-safe lazy creation: a second concurrent insert is silently ignored by the unique key. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO wallets (user_email, balance_paise, created_at, updated_at) "
            + "VALUES (:email, 0, :now, :now)", nativeQuery = true)
    int insertIfAbsent(@Param("email") String email, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("update Wallet w set w.balancePaise = w.balancePaise + :amount, w.updatedAt = :now "
            + "where w.userEmail = :email")
    int credit(@Param("email") String email, @Param("amount") long amount, @Param("now") LocalDateTime now);

    /** Returns 0 when the balance is insufficient - nothing is changed in that case. */
    @Modifying(flushAutomatically = true)
    @Query("update Wallet w set w.balancePaise = w.balancePaise - :amount, w.updatedAt = :now "
            + "where w.userEmail = :email and w.balancePaise >= :amount")
    int debitIfSufficient(@Param("email") String email, @Param("amount") long amount,
                          @Param("now") LocalDateTime now);

    /** Scalar read straight from the database, never from the persistence context. */
    @Query("select w.balancePaise from Wallet w where w.userEmail = :email")
    Optional<Long> findBalance(@Param("email") String email);

    @Query("select coalesce(sum(w.balancePaise), 0) from Wallet w")
    long totalBalancePaise();
}
