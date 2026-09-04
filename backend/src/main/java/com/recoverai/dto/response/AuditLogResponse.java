package com.recoverai.dto.response;

import com.recoverai.entity.AuditLog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID recoveryCaseId,
        Instant timestamp,
        String actor,
        String action,
        String previousState,
        String newState,
        String reason,
        Map<String, Object> metadata
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getRecoveryCase() != null ? log.getRecoveryCase().getId() : null,
                log.getTimestamp(),
                log.getActor().name(),
                log.getAction(),
                log.getPreviousState(),
                log.getNewState(),
                log.getReason(),
                log.getMetadata()
        );
    }
}
