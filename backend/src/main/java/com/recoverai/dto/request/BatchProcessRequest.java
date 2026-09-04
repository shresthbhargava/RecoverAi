package com.recoverai.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchProcessRequest(
        @NotEmpty @Valid List<PaymentEventRequest> events
) {
}
