package com.colaborapp.sales.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PosSaleItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity) {
}
