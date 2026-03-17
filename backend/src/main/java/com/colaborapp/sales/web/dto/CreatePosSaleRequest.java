package com.colaborapp.sales.web.dto;

import com.colaborapp.sales.domain.PaymentMethod;

public record CreatePosSaleRequest(
        PaymentMethod paymentMethod,
        Long marketId) {
}
