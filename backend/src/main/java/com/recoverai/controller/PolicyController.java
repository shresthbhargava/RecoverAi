package com.recoverai.controller;

import com.recoverai.dto.request.PolicyRuleUpdateRequest;
import com.recoverai.dto.response.PolicyRuleResponse;
import com.recoverai.service.PolicyRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyRuleService policyRuleService;

    @GetMapping
    public List<PolicyRuleResponse> listAll() {
        return policyRuleService.listAll();
    }

    @PutMapping("/{id}")
    public PolicyRuleResponse update(@PathVariable UUID id, @Valid @RequestBody PolicyRuleUpdateRequest request) {
        return policyRuleService.update(id, request);
    }
}
