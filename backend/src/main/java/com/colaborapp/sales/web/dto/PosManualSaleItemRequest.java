package com.colaborapp.sales.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PosManualSaleItemRequest(
        @NotNull Long storeId,
        @NotBlank @Size(max = 180) String itemName,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 120) String reference,
        @NotNull @Min(1) Integer quantity) {
}
