package com.colaborapp.promotions.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record PromotionProductAssignmentRequest(
        @NotEmpty List<Long> productIds) {
}
