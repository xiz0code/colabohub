package com.colaborapp.reports.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CollaboratorSalesReportResponse(
        Long collaboratorUserId,
        String collaboratorName,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal totalAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal totalNetAmount,
        BigDecimal totalIvaAmount,
        List<CollaboratorSaleEntryResponse> entries) {
}
