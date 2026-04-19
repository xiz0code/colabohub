package com.colaborapp.products.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductPromotionGroupRequest(
        Long storeId,
        Long ownerUserId,
        @NotBlank @Size(max = 120) String name) {
}
