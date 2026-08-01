package com.colaborapp.promotions.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.colaborapp.promotions.domain.PromotionType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PromotionCampaignRequest(
        Long ownerUserId,
        @NotBlank @Size(max = 180) String name,
        @NotNull PromotionType type,
        Integer quantity,
        @DecimalMin("0.01") BigDecimal promotionalPrice,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentageDiscount,
        @DecimalMin("0.01") BigDecimal minimumPurchaseAmount,
        Boolean appliesToCash,
        Boolean appliesToDebit,
        Boolean appliesToCredit,
        Boolean appliesToTransfer,
        LocalDate startsAt,
        LocalDate endsAt,
        Boolean active) {
}
