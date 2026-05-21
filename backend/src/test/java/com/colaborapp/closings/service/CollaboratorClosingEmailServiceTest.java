package com.colaborapp.closings.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.mail.service.MailService;

@ExtendWith(MockitoExtension.class)
class CollaboratorClosingEmailServiceTest {

    @Mock
    private MailService mailService;

    @Mock
    private ClosingEmailTemplateRenderer templateRenderer;

    @InjectMocks
    private CollaboratorClosingEmailService collaboratorClosingEmailService;

    @Test
    void shouldIncludeProductBreakdownInDailySummaryEmail() {
        CollaboratorSalesSummaryService.CollaboratorSummary summary =
                new CollaboratorSalesSummaryService.CollaboratorSummary(
                        7L,
                        "Camila",
                        "camila@example.com",
                        false,
                        2L,
                        5L,
                        new BigDecimal("12000.0000"),
                        new BigDecimal("600.0000"),
                        new BigDecimal("11400.0000"),
                        new BigDecimal("1916.0000"),
                        new BigDecimal("1916.0000"),
                        List.of(
                                new CollaboratorSalesSummaryService.CollaboratorProductSummary(
                                        "Sticker BTS",
                                        "STK-001",
                                        3,
                                        new BigDecimal("7000.0000"),
                                        new BigDecimal("350.0000"),
                                        new BigDecimal("6650.0000")),
                                new CollaboratorSalesSummaryService.CollaboratorProductSummary(
                                        "Llavero TXT",
                                        "LLV-002",
                                        2,
                                        new BigDecimal("5000.0000"),
                                        new BigDecimal("250.0000"),
                                        new BigDecimal("4750.0000"))),
                        List.of(
                                new CollaboratorSalesSummaryService.CollaboratorSaleEntry(
                                        101L,
                                        "V-101",
                                        Instant.parse("2026-03-18T15:30:00Z"),
                                        "DEBIT_CARD",
                                        "Sticker BTS",
                                        3,
                                        new BigDecimal("2500.0000"),
                                        new BigDecimal("39485.6500"),
                                        new BigDecimal("67.0000"),
                                        new BigDecimal("7000.0000"),
                                        new BigDecimal("200.0000"),
                                        new BigDecimal("100.0000"),
                                        new BigDecimal("50.0000"),
                                        new BigDecimal("350.0000"),
                                        new BigDecimal("6650.0000")),
                                new CollaboratorSalesSummaryService.CollaboratorSaleEntry(
                                        102L,
                                        "V-102",
                                        Instant.parse("2026-03-18T16:00:00Z"),
                                        "CASH",
                                        "Llavero TXT",
                                        2,
                                        new BigDecimal("2500.0000"),
                                        new BigDecimal("39485.6500"),
                                        new BigDecimal("0.0000"),
                                        new BigDecimal("5000.0000"),
                                        new BigDecimal("150.0000"),
                                        new BigDecimal("50.0000"),
                                        new BigDecimal("50.0000"),
                                        new BigDecimal("250.0000"),
                                        new BigDecimal("4750.0000"))));
        when(templateRenderer.renderCollaboratorDailyHtml("Sakura Store", LocalDate.of(2026, 3, 18), summary))
                .thenReturn("<html><body><h1>Resumen diario</h1><p>Detalle del pedido</p><p>Precio</p><p>Medio de pago</p><p>Sticker BTS</p><p>Comisión fija</p><p>Total tienda</p></body></html>");

        collaboratorClosingEmailService.sendDailySummaries(
                "Sakura Store",
                LocalDate.of(2026, 3, 18),
                List.of(summary));

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendHtml(eq("camila@example.com"), eq("Resumen diario - Sakura Store - 2026-03-18"), htmlCaptor.capture(), bodyCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(bodyCaptor.getValue())
                .contains("Detalle del pedido:")
                .contains("18-03-2026 12:30 | Sticker BTS | Cantidad: 3 | Precio: $2.500 | Medio de pago: Débito")
                .contains("Comisión fija: $200")
                .contains("Comisión variable: $100")
                .contains("UF: $39.485,65")
                .contains("Total cliente: $7.000")
                .contains("Total tienda: $6.650");
        org.assertj.core.api.Assertions.assertThat(htmlCaptor.getValue())
                .contains("Resumen diario")
                .contains("Detalle del pedido")
                .contains("Medio de pago")
                .contains("Sticker BTS")
                .contains("Comisión fija")
                .contains("Total tienda");
    }
}
