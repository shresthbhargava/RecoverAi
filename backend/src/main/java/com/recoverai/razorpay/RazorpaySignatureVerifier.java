package com.recoverai.razorpay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies the X-Razorpay-Signature header on inbound webhooks.
 *
 * Razorpay signs the EXACT raw request body with HMAC-SHA256 keyed on the webhook
 * secret, then hex-encodes it. Two consequences that are easy to get wrong:
 *
 *   1. The controller must take the body as a String, never as a parsed DTO.
 *      Jackson round-tripping reorders keys and normalises whitespace, and the
 *      re-serialised bytes will not match the signature Razorpay computed.
 *   2. The comparison must be constant-time. A plain String.equals() leaks how many
 *      leading bytes were correct through timing, which is enough to forge a
 *      signature byte by byte. MessageDigest.isEqual() is the JDK's constant-time
 *      comparison and costs nothing here.
 */
@Slf4j
@Component
public class RazorpaySignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;

    public RazorpaySignatureVerifier(@Value("${razorpay.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * @return true only if the secret is configured AND the signature matches.
     *         A missing secret returns false rather than throwing: an unconfigured
     *         demo box should reject webhooks, not crash on every delivery.
     */
    public boolean isValid(String rawBody, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("razorpay.webhook-secret is not configured — rejecting webhook. "
                    + "Set RAZORPAY_WEBHOOK_SECRET to the value you entered in the Razorpay dashboard.");
            return false;
        }
        if (rawBody == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }

        try {
            String expected = hmacHex(rawBody);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Signature verification failed unexpectedly: {}", ex.getMessage());
            return false;
        }
    }

    /** Exposed so tests (and the demo script) can produce a correctly signed payload. */
    public String hmacHex(String rawBody) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    public boolean isConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
