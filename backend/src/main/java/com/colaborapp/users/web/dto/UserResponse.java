package com.colaborapp.users.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String contactName,
        String description,
        BigDecimal monthlyRent,
        LocalDate startDate,
        String standNumber,
        boolean factura,
        List<String> roles,
        List<Long> marketIds,
        List<Long> storeIds,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
