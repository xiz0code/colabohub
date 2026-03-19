package com.colaborapp.closings.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MonthlyClosingResponse(
        Long marketId,
        String marketName,
        LocalDate closingMonth,
        long saleCount,
        BigDecimal totalSalesAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal totalNetAmount,
        BigDecimal totalIvaAmount,
        BigDecimal totalIvaToPayAmount,
        Instant closedAt,
        String closedBy,
        List<MonthlyClosingCollaboratorResponse> collaborators) {
}
