package com.colaborapp.sales.web.dto;

import com.colaborapp.sales.domain.PaymentMethod;

import jakarta.validation.constraints.NotNull;

public record PosPaymentMethodUpdateRequest(@NotNull PaymentMethod paymentMethod) {
}
