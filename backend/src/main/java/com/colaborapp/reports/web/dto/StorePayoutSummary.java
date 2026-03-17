package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;

public record StorePayoutSummary(
        Long storeId,
        String storeName,
        BigDecimal subtotal,
        BigDecimal commission,
        BigDecimal netAmount,
        long saleCount) {
}
