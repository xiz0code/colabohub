package com.colaborapp.inventory.web.dto;

import java.time.Instant;

import com.colaborapp.inventory.domain.StockMovementType;

public record StockMovementResponse(
        Long id,
        Long productId,
        Long storeId,
        String storeName,
        StockMovementType type,
        Integer quantity,
        Integer previousStock,
        Integer newStock,
        String referenceType,
        Long referenceId,
        Instant createdAt,
        String createdBy) {
}
