package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CollaboratorSaleEntryResponse(
        Long saleId,
        String saleNumber,
        Instant confirmedAt,
        String productName,
        int quantity,
        BigDecimal totalAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount) {
}
