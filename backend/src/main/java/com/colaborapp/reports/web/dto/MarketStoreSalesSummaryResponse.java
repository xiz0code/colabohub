package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;

public record MarketStoreSalesSummaryResponse(
        Long storeId,
        String storeName,
        BigDecimal totalSales,
        long totalItems) {
}
