package com.colaborapp.settings.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;

public record MarketCommissionSettingsRequest(
        boolean overrideEnabled,
        @DecimalMin("0.00") BigDecimal commissionUfValue,
        @DecimalMin("0.00") BigDecimal commissionPercentageValue,
        boolean globalPromotionEnabled,
        @DecimalMin("0.00") BigDecimal globalPromotionPercentage) {
}
