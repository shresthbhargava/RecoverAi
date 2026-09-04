package com.recoverai.controller;

import com.recoverai.dto.response.AgentDecisionResponse;
import com.recoverai.realtime.ActivityStreamPublisher;
import com.recoverai.repository.AgentDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/agent-decisions")
@RequiredArgsConstructor
public class AgentDecisionController {

    private final AgentDecisionRepository agentDecisionRepository;
    private final ActivityStreamPublisher activityStreamPublisher;

    @GetMapping("/recent")
    public List<AgentDecisionResponse> recent(@RequestParam(defaultValue = "50") int limit) {
        return agentDecisionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(AgentDecisionResponse::from)
                .toList();
    }

    /**
     * Screen 2's live feed. text/event-stream, one long-lived connection per browser tab.
     * Reconnection is handled by the browser's own EventSource retry — no client work needed.
     *
     * Unlike /recent (a repository read), this is fed by a live producer: the batch
     * orchestrator and executor publish into ActivityStreamPublisher as a run progresses.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return activityStreamPublisher.subscribe();
    }
}
