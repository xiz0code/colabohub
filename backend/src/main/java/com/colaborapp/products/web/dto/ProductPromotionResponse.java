package com.colaborapp.products.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.promotions.domain.PromotionType;

public record ProductPromotionResponse(
        PromotionType type,
        Integer quantity,
        BigDecimal promotionalPrice,
        BigDecimal percentageDiscount,
        BigDecimal minimumPurchaseAmount,
        Boolean appliesToCash,
        Boolean appliesToDebit,
        Instant endsAt) {
}
