package com.colaborapp.products.web.dto;

import java.math.BigDecimal;

import com.colaborapp.promotions.domain.PromotionType;

public record ProductPromotionResponse(
        PromotionType type,
        Integer quantity,
        BigDecimal promotionalPrice,
        BigDecimal percentageDiscount) {
}
