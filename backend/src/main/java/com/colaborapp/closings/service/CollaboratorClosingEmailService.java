package com.colaborapp.closings.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.colaborapp.mail.service.MailService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollaboratorClosingEmailService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MailService mailService;

    public void sendDailySummaries(String marketName, LocalDate closingDate, List<CollaboratorSalesSummaryService.CollaboratorSummary> collaborators) {
        for (var collaborator : collaborators) {
            if (collaborator.collaboratorEmail() == null || collaborator.collaboratorEmail().isBlank()) {
                continue;
            }
            String subject = "Resumen diario - " + marketName + " - " + DATE_FORMATTER.format(closingDate);
            String body = """
                    Hola %s,

                    Este es tu resumen diario de ventas en %s para la fecha %s.

                    Ventas: %s
                    Comisiones: %s
                    Neto: %s
                    IVA total: %s
                    IVA a pagar: %s
                    Items vendidos: %s
                    
                    Detalle de productos:
                    %s
                    """.formatted(
                    collaborator.collaboratorName(),
                    marketName,
                    DATE_FORMATTER.format(closingDate),
                    collaborator.totalSalesAmount(),
                    collaborator.totalCommissionAmount(),
                    collaborator.totalNetAmount(),
                    collaborator.totalIvaAmount(),
                    collaborator.ivaToPayAmount(),
                    collaborator.totalItems(),
                    formatProducts(collaborator.products()));
            mailService.send(collaborator.collaboratorEmail(), subject, body);
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
                Comisiones del mes: %s
                Neto del mes: %s
                IVA total: %s
                IVA a pagar: %s
                Factura: %s
                
                Detalle de productos:
                %s
                """.formatted(
                collaborator.collaboratorName(),
                marketName,
                collaborator.totalSalesAmount(),
                collaborator.totalCommissionAmount(),
                collaborator.totalNetAmount(),
                collaborator.totalIvaAmount(),
                collaborator.ivaToPayAmount(),
                collaborator.factura() ? "Si" : "No",
                formatProducts(collaborator.products()));
        mailService.send(collaborator.collaboratorEmail(), subject, body);
    }

    private String formatProducts(List<CollaboratorSalesSummaryService.CollaboratorProductSummary> products) {
        if (products == null || products.isEmpty()) {
            return "- No hubo productos vendidos en este periodo.";
        }
        return products.stream()
                .map(product -> "- %s (%s) | Cantidad: %s | Ventas: %s | Comision: %s | Neto: %s".formatted(
                        product.productName(),
                        product.productSku() == null || product.productSku().isBlank() ? "sin SKU" : product.productSku(),
                        product.totalQuantity(),
                        product.totalSalesAmount(),
                        product.totalCommissionAmount(),
                        product.totalNetAmount()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
