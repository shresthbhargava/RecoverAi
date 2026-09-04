package com.recoverai.controller;

import com.recoverai.dto.request.ApprovalDecisionRequest;
import com.recoverai.dto.response.ApprovalResultResponse;
import com.recoverai.dto.response.RecoveryCaseDetailResponse;
import com.recoverai.dto.response.RecoveryCaseSummaryResponse;
import com.recoverai.entity.enums.CaseStatus;
import com.recoverai.service.ApprovalService;
import com.recoverai.service.RecoveryCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recovery-cases")
@RequiredArgsConstructor
public class RecoveryCaseController {

    private final RecoveryCaseService recoveryCaseService;
    private final ApprovalService approvalService;

    @GetMapping
    public Page<RecoveryCaseSummaryResponse> list(
            @RequestParam(required = false) CaseStatus status,
            Pageable pageable
    ) {
        return recoveryCaseService.list(status, pageable);
    }

    @GetMapping("/{id}")
    public RecoveryCaseDetailResponse getDetail(@PathVariable UUID id) {
        return recoveryCaseService.getDetail(id);
    }

    /** The escalation gate. A case parked in AWAITING_APPROVAL resolves through here. */
    @PostMapping("/{id}/escalate/approve")
    public ApprovalResultResponse approveEscalation(@PathVariable UUID id,
                                                    @Valid @RequestBody ApprovalDecisionRequest request) {
        return approvalService.approve(id, request);
    }

    @PostMapping("/{id}/escalate/reject")
    public ApprovalResultResponse rejectEscalation(@PathVariable UUID id,
                                                   @Valid @RequestBody ApprovalDecisionRequest request) {
        return approvalService.reject(id, request);
    }
}

