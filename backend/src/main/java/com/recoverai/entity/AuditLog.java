package com.recoverai.entity;

import com.recoverai.entity.enums.Actor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    /** Nullable: a small number of system-level audit rows (e.g. batch start) are not tied to a single case. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_case_id")
    private RecoveryCase recoveryCase;

    @Column(nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Actor actor;

    @Column(nullable = false, length = 128)
    private String action;

    @Column(name = "previous_state", length = 64)
    private String previousState;

    @Column(name = "new_state", length = 64)
    private String newState;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    void onCreate() {
        if (timestamp == null) timestamp = Instant.now();
    }
}
