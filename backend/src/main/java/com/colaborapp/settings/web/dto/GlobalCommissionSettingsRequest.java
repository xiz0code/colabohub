package com.colaborapp.settings.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record GlobalCommissionSettingsRequest(
        @NotNull @DecimalMin("0.00") BigDecimal commissionUfValue,
        @NotNull @DecimalMin("0.00") BigDecimal commissionPercentageValue) {
}
