package com.colaborapp.products.web.dto;

import java.math.BigDecimal;

public record ProductSellerView(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        Integer stock,
        String ownerFullName,
        Boolean hasPromotion,
        Boolean available) {
}
