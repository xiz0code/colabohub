package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;

public record StoreSalesSummaryResponse(
        Long storeId,
        String storeName,
        long salesCount,
        BigDecimal subtotalAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal netAmount) {
}
