package com.colaborapp.closings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.colaborapp.mail.service.MailService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollaboratorClosingEmailService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Santiago");

    private final MailService mailService;
    private final ClosingEmailTemplateRenderer templateRenderer;

    public void sendDailySummaries(String marketName, LocalDate closingDate, List<CollaboratorSalesSummaryService.CollaboratorSummary> collaborators) {
        for (var collaborator : collaborators) {
            if (collaborator.collaboratorEmail() == null || collaborator.collaboratorEmail().isBlank()) {
                continue;
            }
            String subject = "Resumen diario - " + marketName + " - " + DATE_FORMATTER.format(closingDate);
            String body = """
                    Hola %s,

                    Este es tu resumen diario de ventas en %s para la fecha %s.

                    Ventas confirmadas: %s
                    Unidades vendidas: %s
                    Total cobrado al cliente: %s
                    IVA pagado por cliente: %s
                    Comisiones del Espacio: %s
                    Total a recibir: %s

                    Detalle del pedido:
                    %s
                    """.formatted(
                    collaborator.collaboratorName(),
                    marketName,
                    DATE_FORMATTER.format(closingDate),
                    collaborator.saleCount(),
                    collaborator.totalItems(),
                    formatMoney(collaborator.totalSalesAmount()),
                    formatMoney(collaborator.totalIvaAmount()),
                    formatMoney(collaborator.totalCommissionAmount()),
                    formatMoney(collaborator.totalNetAmount()),
                    formatSales(collaborator.saleEntries()));
            String html = templateRenderer.renderCollaboratorDailyHtml(marketName, closingDate, collaborator);
            mailService.sendHtml(collaborator.collaboratorEmail(), subject, html, body);
        }
    }

    public void sendMonthlySummary(String marketName, LocalDate closingMonth, CollaboratorSalesSummaryService.CollaboratorSummary collaborator) {
        if (collaborator.collaboratorEmail() == null || collaborator.collaboratorEmail().isBlank()) {
            return;
        }
        String subject = "Resumen mensual - " + marketName + " - " + closingMonth.getYear() + "-" + String.format("%02d", closingMonth.getMonthValue());
        String body = """
                Hola %s,

                Este es tu cierre mensual en %s.

                Ventas del mes: %s
                Unidades vendidas: %s
                Total cobrado al cliente: %s
                IVA pagado por cliente: %s
                Comisiones del Espacio: %s
                Total a recibir: %s
                IVA a pagar: %s
                ¿Debes pagar IVA?: %s

                    Detalle del pedido:
                    %s
                    """.formatted(
                collaborator.collaboratorName(),
                marketName,
                collaborator.saleCount(),
                collaborator.totalItems(),
                formatMoney(collaborator.totalSalesAmount()),
                formatMoney(collaborator.totalIvaAmount()),
                formatMoney(collaborator.totalCommissionAmount()),
                formatMoney(collaborator.totalNetAmount()),
                formatMoney(collaborator.ivaToPayAmount()),
                collaborator.factura() ? "No" : "Sí",
                formatSales(collaborator.saleEntries()));
        String html = templateRenderer.renderCollaboratorMonthlyHtml(marketName, closingMonth, collaborator);
        mailService.sendHtml(collaborator.collaboratorEmail(), subject, html, body);
    }

    private String formatSales(List<CollaboratorSalesSummaryService.CollaboratorSaleEntry> saleEntries) {
        if (saleEntries == null || saleEntries.isEmpty()) {
            return "- No hubo ventas en este periodo.";
        }
        return saleEntries.stream()
                .map(entry -> "- %s | %s | Cantidad: %s | Precio: %s | Medio de pago: %s | UF: %s | Comisión fija: %s | Comisión variable: %s | IVA comisión: %s | Total cliente: %s | Total tienda: %s".formatted(
                        formatDateTime(entry.confirmedAt()),
                        entry.productName(),
                        entry.quantity(),
                        formatMoney(entry.unitPrice()),
                        translatePaymentMethod(entry.paymentMethod()),
                        formatDecimal(entry.ufValue()),
                        formatMoney(entry.fixedCommissionAmount()),
                        formatMoney(entry.variableCommissionAmount()),
                        formatMoney(entry.commissionIvaAmount()),
                        formatMoney(entry.totalAmount()),
                        formatMoney(entry.netAmount())))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String formatDateTime(Instant value) {
        if (value == null) {
            return "-";
        }
        return DATE_TIME_FORMATTER.format(value.atZone(BUSINESS_ZONE));
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.HALF_UP);
        return "$" + safe.toPlainString().replaceAll("\\B(?=(\\d{3})+(?!\\d))", ".");
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        BigDecimal safe = value.setScale(2, RoundingMode.HALF_UP);
        String[] parts = safe.toPlainString().split("\\.");
        String integerPart = parts[0].replaceAll("\\B(?=(\\d{3})+(?!\\d))", ".");
        String decimalPart = parts.length > 1 ? parts[1] : "00";
        return "$" + integerPart + "," + decimalPart;
    }

    private String translatePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "-";
        }
        return switch (paymentMethod) {
            case "DEBIT_CARD" -> "Débito";
            case "CREDIT_CARD" -> "Crédito";
            case "CASH" -> "Efectivo";
            case "TRANSFER" -> "Transferencia";
            default -> paymentMethod;
        };
    }
}
