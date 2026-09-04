package com.recoverai.repository;

import com.recoverai.entity.AgentDecision;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentDecisionRepository extends JpaRepository<AgentDecision, UUID> {

    List<AgentDecision> findByRecoveryCaseIdOrderByCreatedAtAsc(UUID recoveryCaseId);

    List<AgentDecision> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
