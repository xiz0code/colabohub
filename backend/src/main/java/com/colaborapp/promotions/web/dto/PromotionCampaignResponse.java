package com.colaborapp.promotions.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.colaborapp.promotions.domain.PromotionType;

public record PromotionCampaignResponse(
        Long id,
        Long storeId,
        String storeName,
        Long ownerUserId,
        String ownerFullName,
        String name,
        PromotionType type,
        Integer quantity,
        BigDecimal promotionalPrice,
        BigDecimal percentageDiscount,
        BigDecimal minimumPurchaseAmount,
        boolean appliesToCash,
        boolean appliesToDebit,
        boolean appliesToCredit,
        boolean appliesToTransfer,
        boolean active,
        Instant startsAt,
        Instant endsAt,
        long productCount,
        List<PromotionProductResponse> products) {
}
