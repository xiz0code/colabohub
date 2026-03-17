package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;

public record SaleTodayItemResponse(
        Long itemId,
        String productName,
        String collaboratorName,
        String storeName,
        Integer quantity,
        BigDecimal subtotal,
        BigDecimal totalCommissionAmount,
        BigDecimal netAmount) {
}
