package com.colaborapp.products.web.dto;

public record ProductPromotionGroupResponse(
        Long id,
        Long storeId,
        Long ownerUserId,
        String ownerFullName,
        String name) {
}
