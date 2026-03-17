package com.colaborapp.closings.web.dto;

import java.math.BigDecimal;

public record DailyClosingStoreResponse(
        Long storeId,
        String storeName,
        long saleCount,
        BigDecimal totalSalesAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal totalNetAmount,
        long totalItems) {
}
