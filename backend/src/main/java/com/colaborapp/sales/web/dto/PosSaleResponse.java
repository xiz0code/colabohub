package com.colaborapp.sales.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.SaleStatus;

public record PosSaleResponse(
        Long id,
        String saleNumber,
        SaleStatus status,
        PaymentMethod paymentMethod,
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
        List<PosSaleItemResponse> items,
        List<PosSaleStoreSummaryResponse> storeSummaries) {
}
