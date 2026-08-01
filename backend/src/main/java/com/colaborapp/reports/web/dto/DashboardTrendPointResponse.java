package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardTrendPointResponse(
        LocalDate date,
        long salesCount,
        BigDecimal totalAmount,
        BigDecimal totalNet) {
}
