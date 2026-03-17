package com.colaborapp.stores.web.dto;

import com.colaborapp.stores.domain.StoreStatus;

import jakarta.validation.constraints.NotNull;

public record StoreStatusUpdateRequest(@NotNull StoreStatus status) {
}
