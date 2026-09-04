package com.recoverai.entity;

import com.recoverai.entity.enums.AttemptStatus;
import com.recoverai.entity.enums.RecoveryAction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recovery_case_id", nullable = false)
    private RecoveryCase recoveryCase;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecoveryAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttemptStatus status;

    /**
     * true  = this attempt made a real Razorpay Test Mode API call
     * false = this attempt was simulated (e.g. SMS/email reminder, incentive)
     * Surfaced directly in the UI so real vs. simulated actions are never conflated.
     */
    @Builder.Default
    @Column(name = "is_real_api_action", nullable = false)
    private Boolean isRealApiAction = false;

    /**
     * The Razorpay object this attempt created — an order id (order_xxx) or a payment
     * link id (plink_xxx). Null for simulated actions, which have no Razorpay object.
     *
     * This is the join key for inbound webhooks: a payment.captured event carries
     * order_id, and this column is the only way back from that to a RecoveryCase.
     */
    @Column(name = "external_ref", length = 64)
    private String externalRef;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(length = 255)
    private String result;

    @Builder.Default
    @Column(name = "amount_recovered", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRecovered = BigDecimal.ZERO;

    @PrePersist
    void onCreate() {
        if (executedAt == null) executedAt = Instant.now();
    }
}
