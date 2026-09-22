package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Append-only ledger. Every paisa that enters or leaves a wallet has exactly
 * one row here, with the balance immediately after it, so a wallet can always
 * be audited back to zero.
 */
@Entity
@Table(
        name = "wallet_transactions",
        indexes = @Index(name = "idx_wtx_user_created", columnList = "user_email, created_at")
)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 191)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WalletTxnType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletTxnSource source;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "balance_after_paise", nullable = false)
    private long balanceAfterPaise;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public WalletTransaction() {}

    public WalletTransaction(String userEmail, WalletTxnType type, WalletTxnSource source,
                             long amountPaise, long balanceAfterPaise,
                             String referenceId, String description, LocalDateTime createdAt) {
        this.userEmail = userEmail;
        this.type = type;
        this.source = source;
        this.amountPaise = amountPaise;
        this.balanceAfterPaise = balanceAfterPaise;
        this.referenceId = referenceId;
        this.description = description == null ? null
                : (description.length() > 255 ? description.substring(0, 255) : description);
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public WalletTxnType getType() { return type; }
    public WalletTxnSource getSource() { return source; }
    public long getAmountPaise() { return amountPaise; }
    public long getBalanceAfterPaise() { return balanceAfterPaise; }
    public String getReferenceId() { return referenceId; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
}
