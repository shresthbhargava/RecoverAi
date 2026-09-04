package com.recoverai.service;

import com.recoverai.dto.request.PolicyRuleUpdateRequest;
import com.recoverai.dto.response.PolicyRuleResponse;
import com.recoverai.entity.PolicyRule;
import com.recoverai.exception.ResourceNotFoundException;
import com.recoverai.repository.PolicyRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read/write access to policy_rule, plus typed getters the (Phase 3) Policy
 * Engine uses to enforce bounds. Rules are cached per-request by JPA's
 * first-level cache; no in-memory caching layer here on purpose — merchants
 * editing a policy via /api/policies must take effect on the very next
 * evaluation, not after some TTL.
 */
@Service
@RequiredArgsConstructor
public class PolicyRuleService {

    private final PolicyRuleRepository policyRuleRepository;

    public List<PolicyRuleResponse> listAll() {
        return policyRuleRepository.findAll().stream()
                .map(PolicyRuleResponse::from)
                .toList();
    }

    public PolicyRuleResponse update(UUID id, PolicyRuleUpdateRequest request) {
        PolicyRule rule = policyRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy rule not found: " + id));
        rule.setValue(request.value());
        rule.setEnabled(request.enabled());
        return PolicyRuleResponse.from(policyRuleRepository.save(rule));
    }

    private PolicyRule getRule(String name) {
        return policyRuleRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Policy rule not seeded: " + name));
    }

    public boolean isEnabled(String name) {
        return getRule(name).getEnabled();
    }

    public int getInt(String name, int fallback) {
        PolicyRule rule = getRule(name);
        if (!rule.getEnabled()) return fallback;
        return Integer.parseInt(rule.getValue());
    }

    public BigDecimal getDecimal(String name, BigDecimal fallback) {
        PolicyRule rule = getRule(name);
        if (!rule.getEnabled()) return fallback;
        return new BigDecimal(rule.getValue());
    }

    public boolean getBoolean(String name, boolean fallback) {
        PolicyRule rule = getRule(name);
        if (!rule.getEnabled()) return fallback;
        return Boolean.parseBoolean(rule.getValue());
    }
}
