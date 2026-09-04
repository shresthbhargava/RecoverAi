package com.recoverai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "policy_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyRule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(nullable = false, length = 64)
    private String value;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String description;
}
