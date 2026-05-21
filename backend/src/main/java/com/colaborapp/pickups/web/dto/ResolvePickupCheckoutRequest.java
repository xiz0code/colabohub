package com.colaborapp.pickups.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolvePickupCheckoutRequest(@NotBlank String code) {
}
