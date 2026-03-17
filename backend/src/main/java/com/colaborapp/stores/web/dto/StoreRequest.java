package com.colaborapp.stores.web.dto;

import com.colaborapp.stores.domain.StoreType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StoreRequest(
        @NotNull Long marketId,
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 180) String name,
        @NotNull StoreType type) {
}
