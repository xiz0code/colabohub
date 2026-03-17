package com.colaborapp.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @NotNull Long productId,
        @NotNull Integer quantityDelta,
        @NotBlank @Size(max = 120) String reason) {
}
