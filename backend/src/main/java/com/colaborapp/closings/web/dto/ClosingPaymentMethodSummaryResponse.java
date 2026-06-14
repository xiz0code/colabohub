package com.colaborapp.closings.web.dto;

import java.math.BigDecimal;

public record ClosingPaymentMethodSummaryResponse(
        String paymentMethod,
        long saleCount,
        BigDecimal totalSalesAmount) {
}
