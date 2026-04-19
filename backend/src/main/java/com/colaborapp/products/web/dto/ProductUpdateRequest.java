package com.colaborapp.products.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        Long ownerUserId,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 80) String sku,
        Long promotionGroupId,
        @Size(max = 120) String promotionGroupName,
        @Size(max = 1024) String description,
        @NotNull @DecimalMin("0.01") BigDecimal salePrice,
        @DecimalMin("0.00") BigDecimal cost,
        @NotNull @Min(0) Integer stock,
        ProductPromotionRequest promotion) {

    public ProductUpdateRequest(
            Long ownerUserId,
            String name,
            String sku,
            String description,
            BigDecimal salePrice,
            BigDecimal cost,
            Integer stock,
            ProductPromotionRequest promotion) {
        this(ownerUserId, name, sku, null, null, description, salePrice, cost, stock, promotion);
    }
}
