package com.recoverai.entity;

import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.entity.enums.Diagnosis;
import com.recoverai.entity.enums.RecoveryAction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_case")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryCase {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "amount_at_risk", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountAtRisk;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CaseStatus status = CaseStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private Diagnosis diagnosis;

    @Column(name = "diagnosis_confidence", precision = 4, scale = 3)
    private BigDecimal diagnosisConfidence;

    @Column(name = "recovery_probability", precision = 4, scale = 3)
    private BigDecimal recoveryProbability;

    @Column(name = "expected_recovery_value", precision = 12, scale = 2)
    private BigDecimal expectedRecoveryValue;

    @Builder.Default
    @Column(name = "recovery_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal recoveryCost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_strategy", length = 32)
    private RecoveryAction selectedStrategy;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "last_contact_at")
    private Instant lastContactAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
