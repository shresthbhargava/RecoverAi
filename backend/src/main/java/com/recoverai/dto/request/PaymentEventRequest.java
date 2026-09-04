package com.recoverai.dto.request;

import com.recoverai.entity.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Mirrors the synthetic dataset event shape / real webhook shape.
 * customerRef is an external-facing identifier (e.g. "cust_204") resolved
 * to an internal Customer row on ingestion; new customers are created on demand.
 */
public record PaymentEventRequest(

        @NotBlank String paymentRef,
        @NotBlank String customerRef,
        String customerName,
        String customerEmail,
        String customerPhone,

        @NotNull @Positive BigDecimal amount,
        String currency,

        @NotNull EventType eventType,
        String failureReason,
        String paymentMethod
) {
}
