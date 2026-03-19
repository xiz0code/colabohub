package com.colaborapp.settings.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GlobalFinancialSettingsResponse(
        BigDecimal currentUfValue,
        LocalDate ufLastUpdatedAt,
        boolean useDynamicFixedCommission,
        BigDecimal commissionUfValue,
        BigDecimal commissionPercentageValue) {
}
