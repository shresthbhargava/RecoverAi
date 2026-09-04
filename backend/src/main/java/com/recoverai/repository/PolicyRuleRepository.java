package com.recoverai.repository;

import com.recoverai.entity.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PolicyRuleRepository extends JpaRepository<PolicyRule, UUID> {
    Optional<PolicyRule> findByName(String name);
}
