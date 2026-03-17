package com.colaborapp.sales.web.dto;

import java.math.BigDecimal;

import com.colaborapp.sales.domain.SaleItemPricingType;

public record PosSaleItemResponse(
        Long id,
        Long productId,
        Long storeId,
        String storeName,
        String productName,
        String collaboratorName,
        String sku,
        String barcode,
        Integer quantity,
        BigDecimal baseUnitPrice,
        BigDecimal lineBaseSubtotal,
        BigDecimal promotionDiscountAmount,
        BigDecimal subtotal,
        SaleItemPricingType pricingType,
        Long appliedPromotionId,
        String appliedPromotionName,
        BigDecimal commission1Amount,
        BigDecimal commission2Amount,
        BigDecimal commissionIvaAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal netAmount) {
}
