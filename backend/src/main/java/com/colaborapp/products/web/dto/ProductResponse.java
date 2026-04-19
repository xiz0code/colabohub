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
        Long promotionGroupId,
        String promotionGroupName,
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

    public ProductResponse(
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
        this(id, storeId, storeName, ownerUserId, ownerFullName, null, null, name, sku, description, salePrice, cost, stock, status,
                barcode, hasPromotion, promotion, createdAt, updatedAt);
    }
}
