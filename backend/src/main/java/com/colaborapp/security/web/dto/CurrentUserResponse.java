package com.colaborapp.security.web.dto;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String email,
        String fullName,
        List<String> roles,
        List<Long> marketIds,
        List<Long> storeIds,
        String activeMarketName) {
}
