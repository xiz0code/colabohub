package com.colaborapp.products.web.dto;

import com.colaborapp.products.domain.ProductStatus;

import jakarta.validation.constraints.NotNull;

public record ProductStatusUpdateRequest(@NotNull ProductStatus status) {
}
