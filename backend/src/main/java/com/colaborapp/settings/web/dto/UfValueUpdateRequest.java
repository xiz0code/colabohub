package com.colaborapp.settings.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UfValueUpdateRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal ufValue) {
}
