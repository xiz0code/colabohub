package com.colaborapp.sales.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PosSaleItemScanRequest(
        @NotBlank String query,
        @Min(1) Integer quantity) {
}
