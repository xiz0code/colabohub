package com.colaborapp.settings.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketFinancialSettingsResponse(
        Long marketId,
        String marketName,
        BigDecimal ufValue,
        Instant ufUpdatedAt,
        boolean ufManualOverride,
        boolean useDynamicFixedCommission,
        boolean overrideEnabled,
        BigDecimal globalCommissionUfValue,
        BigDecimal globalCommissionPercentageValue,
        BigDecimal effectiveCommissionUfValue,
        BigDecimal effectiveCommissionPercentageValue,
        boolean globalPromotionEnabled,
        BigDecimal globalPromotionPercentage) {
}
