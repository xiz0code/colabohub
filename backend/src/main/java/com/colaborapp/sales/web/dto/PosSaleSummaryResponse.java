package com.colaborapp.sales.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.SaleStatus;

public record PosSaleSummaryResponse(
        Long id,
        String saleNumber,
        String type,
        Instant dateTime,
        SaleStatus status,
        BigDecimal subtotalAmount,
        BigDecimal netAmount,
        BigDecimal ivaAmount,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        Long marketId,
        String sellerName) {
}
