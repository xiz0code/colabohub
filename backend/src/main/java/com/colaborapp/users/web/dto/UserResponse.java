package com.colaborapp.users.web.dto;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String contactName,
        String description,
        List<String> roles,
        List<Long> marketIds,
        List<Long> storeIds,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
