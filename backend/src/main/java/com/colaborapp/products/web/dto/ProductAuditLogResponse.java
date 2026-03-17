package com.colaborapp.products.web.dto;

import java.time.Instant;

public record ProductAuditLogResponse(
        Long id,
        String fieldName,
        String previousValue,
        String newValue,
        Instant createdAt,
        String createdBy) {
}
