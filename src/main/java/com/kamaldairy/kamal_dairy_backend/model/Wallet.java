package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One prepaid wallet per customer.
 *
 * The balance is never changed by loading this entity, editing it and saving
 * it back - that pattern loses money when two requests race. Every change goes
 * through a single conditional UPDATE in WalletRepository, which holds the row
 * lock for the rest of the transaction. See WalletService.
 */
@Entity
@Table(
        name = "wallets",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_user", columnNames = "user_email")
)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 191)
    private String userEmail;

    @Column(name = "balance_paise", nullable = false)
    private long balancePaise;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Wallet() {}

    public Long getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public long getBalancePaise() { return balancePaise; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
