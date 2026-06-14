package com.colaborapp.closings.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyClosingResponse(
        Long marketId,
        String marketName,
        LocalDate closingDate,
        long saleCount,
        BigDecimal totalSalesAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal totalNetAmount,
        Instant closedAt,
        String closedBy,
        List<ClosingPaymentMethodSummaryResponse> paymentMethods,
        List<DailyClosingStoreResponse> stores) {
}
