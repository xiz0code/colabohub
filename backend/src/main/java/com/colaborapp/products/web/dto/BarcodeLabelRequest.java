package com.colaborapp.products.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record BarcodeLabelRequest(
        @NotEmpty List<@Valid BarcodeLabelItemRequest> items,
        boolean includeCollaboratorName) {

    public record BarcodeLabelItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity) {
    }
}
