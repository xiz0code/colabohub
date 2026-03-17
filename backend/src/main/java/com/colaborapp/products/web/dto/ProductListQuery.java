package com.colaborapp.products.web.dto;

import com.colaborapp.products.domain.ProductStatus;

public record ProductListQuery(
        Long storeId,
        String query,
        ProductStatus status,
        int page,
        int size) {
}
