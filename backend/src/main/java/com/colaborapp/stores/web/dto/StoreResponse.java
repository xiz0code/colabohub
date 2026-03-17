package com.colaborapp.stores.web.dto;

import java.time.Instant;

import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;

public record StoreResponse(
        Long id,
        Long marketId,
        String marketName,
        String code,
        String name,
        StoreType type,
        StoreStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
