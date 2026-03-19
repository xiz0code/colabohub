package com.colaborapp.sales.web.dto;

public record PosProductResponse(
        Long id,
        Long storeId,
        String storeName,
        String collaboratorName,
        String name,
        String sku,
        String barcode,
        Integer stock,
        java.math.BigDecimal salePrice) {
}
