package com.colaborapp.security.web.dto;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String email,
        String fullName,
        boolean active,
        List<String> roles,
        Long activeMarketId,
        String activeMarketName,
        List<Long> marketIds,
        List<Long> storeIds) {
}
