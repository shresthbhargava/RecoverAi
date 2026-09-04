package com.recoverai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GrokClientService {

    private final WebClient grokWebClient;
    private final String model;
    private final long timeoutMs;

    public GrokClientService(
            @Qualifier("grokWebClient") WebClient grokWebClient,
            @Value("${grok.model}") String model,
            @Value("${grok.timeout-ms}") long timeoutMs
    ) {
        this.grokWebClient = grokWebClient;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    public LlmCallResult complete(String systemPrompt, String userPrompt) {
        long start = System.currentTimeMillis();

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.2
        );

        JsonNode response = grokWebClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(300))
                        .filter(this::isRetryable))
                .block();

        int latencyMs = (int) (System.currentTimeMillis() - start);

        String content = response
                .path("choices").path(0)
                .path("message").path("content")
                .asText();

        return new LlmCallResult(content, model, latencyMs);
    }

    private boolean isRetryable(Throwable ex) {
        return !(ex instanceof IllegalArgumentException);
    }

    public record LlmCallResult(String rawJson, String model, int latencyMs) {
    }
}