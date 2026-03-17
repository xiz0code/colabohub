package com.colaborapp.sales.web.dto;

import java.math.BigDecimal;

public record PosSaleStoreSummaryResponse(
        Long storeId,
        String storeName,
        Integer lineCount,
        Integer unitCount,
        BigDecimal subtotalAmount,
        BigDecimal commission1Amount,
        BigDecimal commission2Amount,
        BigDecimal commissionIvaAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal netAmount) {
}
