package com.colaborapp.closings.web.dto;

import java.math.BigDecimal;

public record MonthlyClosingCollaboratorResponse(
        Long collaboratorUserId,
        String collaboratorName,
        String collaboratorEmail,
        boolean factura,
        long saleCount,
        long totalItems,
        BigDecimal totalSalesAmount,
        BigDecimal totalCommissionAmount,
        BigDecimal totalNetAmount,
        BigDecimal totalIvaAmount,
        BigDecimal ivaToPayAmount) {
}
