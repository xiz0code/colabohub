package com.colaborapp.products.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.colaborapp.promotions.domain.PromotionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductPromotionRequest(
        @NotNull PromotionType type,
        @Min(2) Integer quantity,
        @DecimalMin("0.01") BigDecimal promotionalPrice,
        @DecimalMin("0.01") BigDecimal percentageDiscount,
        Boolean appliesToCash,
        Boolean appliesToDebit,
        LocalDate endsAt) {
}
