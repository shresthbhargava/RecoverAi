package com.recoverai.entity;

import com.recoverai.entity.enums.WebhookStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One row per inbound Razorpay webhook delivery — including the ones we reject.
 *
 * Keeping rejected deliveries is deliberate. A table containing only successes proves
 * nothing; a table that also shows INVALID_SIGNATURE and DUPLICATE rows is the evidence
 * that signature verification and the idempotency guard actually fire.
 *
 * Maps the webhook_event table created in V2__webhook_support.sql. Nothing here is
 * Hibernate-generated: ddl-auto is `validate`, so this class must match the migration
 * exactly or the application refuses to start.
 */
@Entity
@Table(name = "webhook_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Razorpay's x-razorpay-event-id header. UNIQUE in the schema, and that constraint is
     * the entire idempotency mechanism — a duplicate delivery fails the insert instead of
     * being detected by a read-then-write, which would race under concurrent retries.
     */
    @Column(name = "razorpay_event_id", nullable = false, unique = true, length = 128)
    private String razorpayEventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Builder.Default
    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookStatus status;

    /**
     * Nullable on purpose: an event can be validly signed and still match nothing of ours,
     * e.g. another integration sharing the same Razorpay Test Mode account.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_case_id")
    private RecoveryCase relatedCase;

    /** The Razorpay order_id or payment link id we correlated on, when we found one. */
    @Column(name = "external_ref", length = 64)
    private String externalRef;

    /**
     * The parsed body, stored as jsonb the same way AuditLog stores its metadata. Kept in
     * full so a mis-parsed event can be diagnosed after the fact without asking Razorpay
     * to redeliver it.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) receivedAt = Instant.now();
    }
}
