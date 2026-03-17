package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MarketSalesTodayReportResponse(
        Long marketId,
        String marketName,
        LocalDate businessDate,
        BigDecimal totalSales,
        long totalItems,
        List<MarketStoreSalesSummaryResponse> salesPerStore) {
}
