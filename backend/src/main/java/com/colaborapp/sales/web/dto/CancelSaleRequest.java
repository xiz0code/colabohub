package com.colaborapp.sales.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelSaleRequest(
        @NotBlank(message = "Ingresa un motivo para anular la venta.") String reason) {
}
