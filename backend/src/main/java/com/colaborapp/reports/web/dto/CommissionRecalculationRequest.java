package com.colaborapp.reports.web.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record CommissionRecalculationRequest(
        @NotNull LocalDate dateFrom,
        @NotNull LocalDate dateTo) {
}
