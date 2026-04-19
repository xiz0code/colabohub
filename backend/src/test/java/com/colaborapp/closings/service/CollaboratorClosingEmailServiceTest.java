package com.colaborapp.closings.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
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
                                        new BigDecimal("4750.0000"))));
        when(templateRenderer.renderCollaboratorDailyHtml("Sakura Store", LocalDate.of(2026, 3, 18), summary))
                .thenReturn("<html><body><h1>Resumen diario</h1><p>Sticker BTS</p><p>Comisiones</p><p>Neto</p></body></html>");

        collaboratorClosingEmailService.sendDailySummaries(
                "Sakura Store",
                LocalDate.of(2026, 3, 18),
                List.of(summary));

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendHtml(eq("camila@example.com"), eq("Resumen diario - Sakura Store - 2026-03-18"), htmlCaptor.capture(), bodyCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(bodyCaptor.getValue())
                .contains("Detalle de productos:")
                .contains("Sticker BTS (STK-001)")
                .contains("Llavero TXT (LLV-002)");
        org.assertj.core.api.Assertions.assertThat(htmlCaptor.getValue())
                .contains("Resumen diario")
                .contains("Sticker BTS")
                .contains("Comisiones")
                .contains("Neto");
    }
}
