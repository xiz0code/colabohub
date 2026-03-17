package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record StoreSalesReportResponse(
        Long storeId,
        String storeName,
        long salesCount,
        BigDecimal subtotalAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal netAmount,
        List<StoreSaleEntryResponse> recentSales) {
}
