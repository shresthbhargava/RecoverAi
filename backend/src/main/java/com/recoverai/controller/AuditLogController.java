package com.recoverai.controller;

import com.recoverai.dto.response.AuditLogResponse;
import com.recoverai.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public List<AuditLogResponse> list(
            @RequestParam(required = false) UUID recoveryCaseId,
            @RequestParam(defaultValue = "200") int limit
    ) {
        var logs = (recoveryCaseId != null)
                ? auditLogRepository.findByRecoveryCaseIdOrderByTimestampAsc(recoveryCaseId)
                : auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));

        return logs.stream().map(AuditLogResponse::from).toList();
    }
}
