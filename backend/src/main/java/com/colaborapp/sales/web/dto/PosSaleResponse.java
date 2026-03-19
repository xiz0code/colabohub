package com.colaborapp.sales.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.SaleStatus;

public record PosSaleResponse(
        Long id,
        String saleNumber,
        Long marketId,
        SaleStatus status,
        PaymentMethod paymentMethod,
        BigDecimal netAmount,
        BigDecimal ivaAmount,
        BigDecimal subtotalAmount,
        BigDecimal totalDiscountAmount,
        BigDecimal totalAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal totalNetAmount,
        BigDecimal ufValue,
        BigDecimal commissionUfValue,
        BigDecimal commissionPercentageValue,
        Instant openedAt,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancelledBy,
        String cancellationReason,
        List<PosSaleItemResponse> items,
        List<PosSaleStoreSummaryResponse> storeSummaries) {
}
