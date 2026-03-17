package com.colaborapp.markets.web.dto;

import java.time.Instant;

public record MarketResponse(
        Long id,
        String name,
        String email,
        String phone,
        String contactName,
        String description,
        String city,
        String currency,
        boolean ufEnabled,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
