package com.recoverai.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String webhookSecret;
    private final String razorpayKeyId;
    private final String razorpayKeySecret;
    private final String grokApiKey;

    public HealthController(
            @Value("${razorpay.webhook-secret:}") String webhookSecret,
            @Value("${razorpay.key-id:}") String razorpayKeyId,
            @Value("${razorpay.key-secret:}") String razorpayKeySecret,
            @Value("${grok.api-key:}") String grokApiKey) {
        this.webhookSecret = webhookSecret;
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
        this.grokApiKey = grokApiKey;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "recoverai-backend");
        body.put("timestamp", Instant.now().toString());
        body.put("timezone", TimeZone.getDefault().getID());

        /*
         * Booleans only — never the values, never their lengths. The point is to answer
         * "did the process actually receive this setting?" without having to grep a log or
         * trigger a failure to find out.
         *
         * This exists because an unset RAZORPAY_WEBHOOK_SECRET is indistinguishable from a
         * mismatched one until a delivery is rejected, and environment variables are read once
         * at startup: setting one in a terminal, or in an IDE run configuration, does nothing
         * for an already-running process. One GET now confirms what the JVM is holding.
         */
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("webhookSecretConfigured", isSet(webhookSecret));
        config.put("razorpayKeysConfigured", isSet(razorpayKeyId) && isSet(razorpayKeySecret));
        config.put("grokApiKeyConfigured", isSet(grokApiKey));
        body.put("config", config);

        /*
         * Degraded is honest, not alarming: with no Grok key both LLM agents fall back to
         * deterministic heuristics, and with no Razorpay keys real API actions fail at the 401.
         * The system still runs end to end — it just reports fewer recoveries, truthfully.
         */
        body.put("mode", isSet(grokApiKey) && isSet(razorpayKeyId)
                ? "FULL"
                : "DEGRADED_FALLBACK");

        return body;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
