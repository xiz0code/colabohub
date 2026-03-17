package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(
        LocalDate businessDate,
        long salesCount,
        BigDecimal totalAmount,
        BigDecimal totalCommission,
        BigDecimal totalNet,
        long activeProducts,
        long lowStockProducts,
        List<StoreSalesSummaryResponse> stores) {
}
