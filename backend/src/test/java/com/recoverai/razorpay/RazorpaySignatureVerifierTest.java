package com.recoverai.razorpay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for the webhook signature gate.
 *
 * No mocks and no Spring context: the verifier's only dependency is a String
 * injected by @Value, so `new RazorpaySignatureVerifier("secret")` is the whole
 * setup. That also lets each test choose its own secret, which is the only way to
 * cover the unconfigured case honestly.
 *
 * The signatures below are not pasted-in constants. Each test derives the expected
 * HMAC from the verifier itself, then mutates it. A hardcoded hex string would only
 * prove that two copies of the same mistake agree with each other.
 */
class RazorpaySignatureVerifierTest {

    private static final String SECRET = "whsec_recoverai_demo_2026";

    /** A realistic payment.captured body — signing is over these exact bytes. */
    private static final String RAW_BODY = """
            {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_test123",\
            "order_id":"order_DEMO0001","amount":1500000,"status":"captured"}}}}""";

    private final RazorpaySignatureVerifier verifier = new RazorpaySignatureVerifier(SECRET);

    @Test
    @DisplayName("A signature computed over the exact body is accepted")
    void correctSignatureIsAccepted() throws Exception {
        String signature = verifier.hmacHex(RAW_BODY);

        assertThat(verifier.isValid(RAW_BODY, signature)).isTrue();
    }

    @Test
    @DisplayName("HMAC-SHA256 hex is 64 characters, lowercase")
    void hmacIsWellFormedHex() throws Exception {
        String signature = verifier.hmacHex(RAW_BODY);

        // 32 bytes hex-encoded. If this ever changes shape, the algorithm changed.
        assertThat(signature).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Surrounding whitespace on the header is tolerated")
    void headerIsTrimmedBeforeComparison() throws Exception {
        String signature = verifier.hmacHex(RAW_BODY);

        assertThat(verifier.isValid(RAW_BODY, "  " + signature + "\n")).isTrue();
    }

    // ---------------------------------------------------------------- rejections

    @Nested
    @DisplayName("Rejections")
    class Rejections {

        @Test
        @DisplayName("Flipping one character of the signature fails — this is what -Tamper does")
        void tamperedSignatureIsRejected() throws Exception {
            String signature = verifier.hmacHex(RAW_BODY);

            // Same mutation the PowerShell script performs: change the first hex digit.
            char first = signature.charAt(0);
            String tampered = (first == 'a' ? 'b' : 'a') + signature.substring(1);

            assertThat(tampered).isNotEqualTo(signature);
            assertThat(verifier.isValid(RAW_BODY, tampered)).isFalse();
        }

        @Test
        @DisplayName("A body modified after signing fails — the whole point of signing")
        void modifiedBodyIsRejected() throws Exception {
            String signature = verifier.hmacHex(RAW_BODY);

            // An attacker inflating the captured amount from 15,000 to 150,000.
            String tamperedBody = RAW_BODY.replace("1500000", "15000000");

            assertThat(tamperedBody).isNotEqualTo(RAW_BODY);
            assertThat(verifier.isValid(tamperedBody, signature)).isFalse();
        }

        @Test
        @DisplayName("Re-serialised JSON fails even when semantically identical")
        void reorderedJsonIsRejected() throws Exception {
            String signature = verifier.hmacHex(RAW_BODY);

            // Exactly what happens if the controller accepts a DTO and Jackson
            // re-serialises it: same meaning, different bytes, dead signature.
            // This is why WebhookController takes @RequestBody String.
            String prettyPrinted = RAW_BODY.replace(",", ", ");

            assertThat(verifier.isValid(prettyPrinted, signature)).isFalse();
        }

        @Test
        @DisplayName("A signature made with a different secret fails")
        void signatureFromAnotherSecretIsRejected() throws Exception {
            String foreignSignature = new RazorpaySignatureVerifier("some_other_secret").hmacHex(RAW_BODY);

            assertThat(verifier.isValid(RAW_BODY, foreignSignature)).isFalse();
        }

        @Test
        @DisplayName("A missing or blank header is rejected without throwing")
        void missingHeaderIsRejected() {
            assertThat(verifier.isValid(RAW_BODY, null)).isFalse();
            assertThat(verifier.isValid(RAW_BODY, "")).isFalse();
            assertThat(verifier.isValid(RAW_BODY, "   ")).isFalse();
        }

        @Test
        @DisplayName("A null body is rejected without throwing")
        void nullBodyIsRejected() throws Exception {
            String signature = verifier.hmacHex(RAW_BODY);

            assertThatCode(() -> verifier.isValid(null, signature)).doesNotThrowAnyException();
            assertThat(verifier.isValid(null, signature)).isFalse();
        }
    }

    // ---------------------------------------------------------------- unconfigured

    @Nested
    @DisplayName("Unconfigured secret")
    class Unconfigured {

        /*
         * This is the state Shresth's box was actually in when every delivery came back
         * INVALID_SIGNATURE: the app had no secret, so it rejected before comparing
         * anything. The value passed to the signing script was irrelevant.
         */

        @Test
        @DisplayName("An empty secret rejects everything rather than throwing")
        void emptySecretRejectsEverything() throws Exception {
            RazorpaySignatureVerifier unconfigured = new RazorpaySignatureVerifier("");

            // Signed correctly by someone who does hold a secret — still rejected,
            // because an app with no secret cannot trust any delivery.
            String validSignature = verifier.hmacHex(RAW_BODY);

            assertThat(unconfigured.isConfigured()).isFalse();
            assertThat(unconfigured.isValid(RAW_BODY, validSignature)).isFalse();
        }

        @Test
        @DisplayName("A null secret behaves the same as an empty one")
        void nullSecretRejectsEverything() {
            RazorpaySignatureVerifier unconfigured = new RazorpaySignatureVerifier(null);

            assertThat(unconfigured.isConfigured()).isFalse();
            assertThat(unconfigured.isValid(RAW_BODY, "anything")).isFalse();
        }

        @Test
        @DisplayName("A whitespace-only secret counts as unconfigured")
        void blankSecretIsTreatedAsUnset() {
            // An env var set to "" or " " by a misfired script is a real failure mode,
            // and it must not be mistaken for a configured secret.
            RazorpaySignatureVerifier unconfigured = new RazorpaySignatureVerifier("   ");

            assertThat(unconfigured.isConfigured()).isFalse();
            assertThat(unconfigured.isValid(RAW_BODY, "anything")).isFalse();
        }

        @Test
        @DisplayName("A configured secret reports itself configured")
        void configuredSecretIsReported() {
            assertThat(verifier.isConfigured()).isTrue();
        }
    }
}
