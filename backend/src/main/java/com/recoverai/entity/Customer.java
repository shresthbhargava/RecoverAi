package com.recoverai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;
    private String email;
    private String phone;

    @Builder.Default
    @Column(name = "total_successful_payments", nullable = false)
    private Integer totalSuccessfulPayments = 0;

    @Builder.Default
    @Column(name = "total_failed_payments", nullable = false)
    private Integer totalFailedPayments = 0;

    @Builder.Default
    @Column(name = "lifetime_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal lifetimeValue = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "opted_out", nullable = false)
    private Boolean optedOut = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
