package com.recoverai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PolicyRuleUpdateRequest(
        @NotBlank String value,
        @NotNull Boolean enabled
) {
}
