package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleTodayDetailResponse(
        Long saleId,
        String saleNumber,
        Instant confirmedAt,
        BigDecimal totalAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal totalNetAmount,
        List<SaleDetailStoreSummaryResponse> stores,
        List<SaleTodayItemResponse> items) {
}
