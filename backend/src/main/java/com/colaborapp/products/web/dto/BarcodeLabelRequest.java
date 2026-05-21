package com.colaborapp.products.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record BarcodeLabelRequest(
        @NotEmpty List<@Valid BarcodeLabelItemRequest> items,
        boolean includeCollaboratorName,
        BarcodeLabelFormat format) {

    public BarcodeLabelRequest {
        if (format == null) {
            format = BarcodeLabelFormat.A4;
        }
    }

    public enum BarcodeLabelFormat {
        A4,
        LETTER,
        LABEL_30X20
    }

    public record BarcodeLabelItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity) {
    }
}
