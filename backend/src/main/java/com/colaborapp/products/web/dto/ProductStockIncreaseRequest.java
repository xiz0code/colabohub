package com.colaborapp.products.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductStockIncreaseRequest(
        @NotNull @Min(1) Integer quantity) {
}
