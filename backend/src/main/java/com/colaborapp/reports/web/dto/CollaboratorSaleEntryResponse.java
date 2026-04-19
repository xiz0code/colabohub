package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CollaboratorSaleEntryResponse(
        Long saleId,
        String saleNumber,
        Instant confirmedAt,
        String paymentMethod,
        String productName,
        String promotionLabel,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal ufValue,
        BigDecimal fixedCommissionAmount,
        BigDecimal variableCommissionAmount,
        BigDecimal commissionIvaAmount,
        BigDecimal totalAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount) {
}
