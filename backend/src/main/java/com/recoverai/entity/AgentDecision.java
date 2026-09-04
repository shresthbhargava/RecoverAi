package com.recoverai.entity;

import com.recoverai.entity.enums.AgentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent_decision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDecision {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recovery_case_id", nullable = false)
    private RecoveryCase recoveryCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 32)
    private AgentType agentType;

    @Column(name = "input_summary", columnDefinition = "TEXT")
    private String inputSummary;

    @Column(length = 64)
    private String decision;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    /**
     * List of plain-language reasoning statements, each grounded in fields
     * actually present in the case input — never an invented fact.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> reasoning;

    @Column(name = "llm_model", length = 64)
    private String llmModel;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
