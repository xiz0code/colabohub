package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;

public record SaleDetailStoreSummaryResponse(
        Long storeId,
        String storeName,
        int lineCount,
        int unitCount,
        BigDecimal subtotalAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal netAmount) {
}
