package com.colaborapp.closings.service;

import static org.mockito.ArgumentMatchers.eq;
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

        collaboratorClosingEmailService.sendDailySummaries(
                "Sakura Store",
                LocalDate.of(2026, 3, 18),
                List.of(summary));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("camila@example.com"), eq("Resumen diario - Sakura Store - 2026-03-18"), bodyCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(bodyCaptor.getValue())
                .contains("Detalle de productos:")
                .contains("Sticker BTS (STK-001)")
                .contains("Llavero TXT (LLV-002)");
    }
}
