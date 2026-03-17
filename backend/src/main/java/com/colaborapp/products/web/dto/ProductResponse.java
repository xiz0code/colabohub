package com.colaborapp.products.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.products.domain.ProductStatus;

public record ProductResponse(
        Long id,
        Long storeId,
        String storeName,
        Long ownerUserId,
        String ownerFullName,
        String name,
        String sku,
        String description,
        BigDecimal salePrice,
        BigDecimal cost,
        Integer stock,
        ProductStatus status,
        String barcode,
        boolean hasPromotion,
        ProductPromotionResponse promotion,
        Instant createdAt,
        Instant updatedAt) {
}
