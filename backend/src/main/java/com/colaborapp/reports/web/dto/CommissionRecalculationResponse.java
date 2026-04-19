package com.colaborapp.reports.web.dto;

import java.time.LocalDate;

public record CommissionRecalculationResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        int reviewedSales,
        int recalculatedSales,
        int ufDatesUsed) {
}
