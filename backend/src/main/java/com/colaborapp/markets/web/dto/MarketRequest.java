package com.colaborapp.markets.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarketRequest(
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 40) String phone,
        @Size(max = 180) String contactName,
        @Size(max = 500) String description,
        @Size(max = 120) String city,
        @Size(max = 10) String currency,
        Boolean ufEnabled,
        Boolean active) {
}
