package com.colaborapp.products.web.dto;

public record RecentBarcodeLabelProductResponse(
        ProductResponse product,
        Integer labelQuantity) {
}
