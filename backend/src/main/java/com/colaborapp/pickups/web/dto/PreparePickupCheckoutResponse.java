package com.colaborapp.pickups.web.dto;

import com.colaborapp.sales.web.dto.PosSaleResponse;

public record PreparePickupCheckoutResponse(
        PickupResponse pickup,
        PosSaleResponse sale) {
}
