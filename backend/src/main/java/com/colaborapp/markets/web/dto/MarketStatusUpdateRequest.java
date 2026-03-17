package com.colaborapp.markets.web.dto;

import jakarta.validation.constraints.NotNull;

public record MarketStatusUpdateRequest(@NotNull Boolean active) {
}
