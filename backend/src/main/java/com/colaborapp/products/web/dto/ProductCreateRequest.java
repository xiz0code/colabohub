package com.colaborapp.products.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        Long storeId,
        Long ownerUserId,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 80) String sku,
        @Size(max = 1024) String description,
        @NotNull @DecimalMin("0.01") BigDecimal salePrice,
        @DecimalMin("0.00") BigDecimal cost,
        @NotNull @Min(0) Integer initialStock,
        ProductPromotionRequest promotion) {
}
