package com.colaborapp.pickups.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.pickups.domain.PickupStatus;

public record PickupResponse(
        Long id,
        Long marketId,
        String marketName,
        Long storeId,
        String storeName,
        String pickupBarcode,
        String pickupNumber,
        String customerName,
        String description,
        boolean payable,
        BigDecimal amountDue,
        PickupStatus status,
        Long linkedSaleId,
        Instant collectedAt,
        String collectedBy,
        Instant createdAt) {
}
