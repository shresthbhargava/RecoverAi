package com.recoverai.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayClientService {

    private final WebClient razorpayWebClient;

    public RazorpayOrderResult createOrder(BigDecimal amount, String currency, String receiptRef) {
        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        Map<String, Object> body = Map.of(
                "amount", amountInPaise,
                "currency", currency,
                "receipt", receiptRef,
                "payment_capture", 1
        );

        JsonNode response = razorpayWebClient.post()
                .uri("/orders")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return new RazorpayOrderResult(
                response.path("id").asText(),
                response.path("status").asText()
        );
    }

    public RazorpayPaymentLinkResult createPaymentLink(BigDecimal amount, String currency,
                                                       String customerName, String customerEmail,
                                                       String customerPhone, String description) {
        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        Map<String, Object> body = Map.of(
                "amount", amountInPaise,
                "currency", currency,
                "description", description,
                "customer", Map.of(
                        "name", customerName == null ? "" : customerName,
                        "email", customerEmail == null ? "" : customerEmail,
                        "contact", customerPhone == null ? "" : customerPhone
                ),
                "notify", Map.of("sms", true, "email", true),
                "reminder_enable", true
        );

        JsonNode response = razorpayWebClient.post()
                .uri("/payment_links")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return new RazorpayPaymentLinkResult(
                response.path("id").asText(),
                response.path("short_url").asText(),
                response.path("status").asText()
        );
    }

    public String fetchPaymentStatus(String razorpayPaymentId) {
        JsonNode response = razorpayWebClient.get()
                .uri("/payments/{id}", razorpayPaymentId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        return response.path("status").asText();
    }

    public record RazorpayOrderResult(String orderId, String status) {
    }

    public record RazorpayPaymentLinkResult(String linkId, String shortUrl, String status) {
    }
}