package com.colaborapp.pickups.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePickupRequest(
        Long storeId,
        @NotBlank @Size(max = 80) String pickupNumber,
        @NotBlank @Size(max = 180) String customerName,
        @NotBlank @Size(max = 500) String description,
        boolean payable,
        @DecimalMin("0.01") BigDecimal amountDue) {
}
