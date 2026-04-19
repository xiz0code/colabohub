package com.colaborapp.common.alert;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.colaborapp.mail.service.MailService;

@ExtendWith(MockitoExtension.class)
class CriticalErrorAlertServiceTest {

    @Mock
    private MailService mailService;

    @Mock
    private CriticalAlertEmailTemplateRenderer templateRenderer;

    @Test
    void shouldSendAlertForUnexpectedErrors() {
        org.mockito.Mockito.when(templateRenderer.renderHtml(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("<html>critica</html>");
        CriticalErrorAlertService service = new CriticalErrorAlertService(mailService, templateRenderer, "ops@colabohub.cl");

        service.notifyIfNeeded(HttpStatus.INTERNAL_SERVER_ERROR, "/api/products", new RuntimeException("Fallo grave"));

        verify(mailService).sendHtml(eq("ops@colabohub.cl"), contains("Alerta critica"), contains("critica"), contains("/api/products"));
    }

    @Test
    void shouldSendAlertForCriticalOperationalEndpointsEvenOnControlledStatus() {
        org.mockito.Mockito.when(templateRenderer.renderHtml(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("<html>critica</html>");
        CriticalErrorAlertService service = new CriticalErrorAlertService(mailService, templateRenderer, "ops@colabohub.cl");

        service.notifyIfNeeded(HttpStatus.BAD_REQUEST, "/api/pos/sales/15/cancel", new RuntimeException("Cancelacion fallo"));

        verify(mailService).sendHtml(eq("ops@colabohub.cl"), contains("Alerta critica"), contains("critica"), contains("/api/pos/sales/15/cancel"));
    }

    @Test
    void shouldIgnoreNonCriticalClientErrors() {
        CriticalErrorAlertService service = new CriticalErrorAlertService(mailService, templateRenderer, "ops@colabohub.cl");

        service.notifyIfNeeded(HttpStatus.BAD_REQUEST, "/api/products", new RuntimeException("Dato invalido"));

        verify(mailService, never()).sendHtml(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldNotBreakIfMailSendingFails() {
        org.mockito.Mockito.when(templateRenderer.renderHtml(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("<html>critica</html>");
        CriticalErrorAlertService service = new CriticalErrorAlertService(mailService, templateRenderer, "ops@colabohub.cl");
        doThrow(new RuntimeException("SMTP down")).when(mailService)
                .sendHtml(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        service.notifyIfNeeded(HttpStatus.INTERNAL_SERVER_ERROR, "/api/inventory/adjustments", new RuntimeException("Stock fallo"));
    }
}
