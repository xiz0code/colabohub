package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesTodayReportResponse(
        LocalDate businessDate,
        BigDecimal totalSales,
        BigDecimal totalAmount,
        BigDecimal totalCommission,
        BigDecimal totalNet,
        long salesCount,
        List<StoreSalesSummaryResponse> stores) {
}
