package com.colaborapp.sales.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PosSaleItemUpdateRequest(
        @NotNull @Min(1) Integer quantity) {
}
