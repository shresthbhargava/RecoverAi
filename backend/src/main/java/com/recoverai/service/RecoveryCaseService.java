package com.recoverai.service;

import com.recoverai.dto.response.AgentDecisionResponse;
import com.recoverai.dto.response.RecoveryAttemptResponse;
import com.recoverai.dto.response.RecoveryCaseDetailResponse;
import com.recoverai.dto.response.RecoveryCaseSummaryResponse;
import com.recoverai.entity.RecoveryCase;
import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.exception.ResourceNotFoundException;
import com.recoverai.repository.AgentDecisionRepository;
import com.recoverai.repository.RecoveryAttemptRepository;
import com.recoverai.repository.RecoveryCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecoveryCaseService {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final AgentDecisionRepository agentDecisionRepository;
    private final RecoveryAttemptRepository recoveryAttemptRepository;

    public Page<RecoveryCaseSummaryResponse> list(CaseStatus status, Pageable pageable) {
        Page<RecoveryCase> page = (status != null)
                ? recoveryCaseRepository.findByStatus(status, pageable)
                : recoveryCaseRepository.findAll(pageable);
        return page.map(RecoveryCaseSummaryResponse::from);
    }

    public RecoveryCaseDetailResponse getDetail(UUID id) {
        RecoveryCase recoveryCase = recoveryCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recovery case not found: " + id));

        var decisions = agentDecisionRepository.findByRecoveryCaseIdOrderByCreatedAtAsc(id).stream()
                .map(AgentDecisionResponse::from)
                .toList();

        var attempts = recoveryAttemptRepository.findByRecoveryCaseIdOrderByAttemptNumberAsc(id).stream()
                .map(RecoveryAttemptResponse::from)
                .toList();

        return RecoveryCaseDetailResponse.from(recoveryCase, decisions, attempts);
    }

    public RecoveryCase getEntity(UUID id) {
        return recoveryCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recovery case not found: " + id));
    }
}
