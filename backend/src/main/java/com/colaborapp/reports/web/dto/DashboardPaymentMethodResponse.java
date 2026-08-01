package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;

public record DashboardPaymentMethodResponse(
        String paymentMethod,
        long salesCount,
        BigDecimal totalAmount) {
}
