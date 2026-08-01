package com.colaborapp.promotions.web.dto;

public record PromotionProductResponse(
        Long productId,
        String name,
        String sku,
        Integer stock) {
}
