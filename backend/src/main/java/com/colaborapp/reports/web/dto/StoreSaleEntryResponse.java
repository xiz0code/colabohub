package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StoreSaleEntryResponse(
        Long saleId,
        String saleNumber,
        Instant confirmedAt,
        Integer lineCount,
        Integer unitCount,
        BigDecimal subtotalAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal netAmount) {
}
