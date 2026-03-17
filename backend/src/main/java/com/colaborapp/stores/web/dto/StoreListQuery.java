package com.colaborapp.stores.web.dto;

import com.colaborapp.stores.domain.StoreStatus;

public record StoreListQuery(
        String query,
        StoreStatus status,
        int page,
        int size) {
}
