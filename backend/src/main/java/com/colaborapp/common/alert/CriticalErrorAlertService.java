package com.colaborapp.common.alert;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.colaborapp.mail.service.MailService;

@Service
public class CriticalErrorAlertService {

    private static final Logger log = LoggerFactory.getLogger(CriticalErrorAlertService.class);

    private final MailService mailService;
    private final CriticalAlertEmailTemplateRenderer templateRenderer;
    private final List<String> recipients;

    public CriticalErrorAlertService(
            MailService mailService,
            CriticalAlertEmailTemplateRenderer templateRenderer,
            @Value("${app.alerts.critical-email:}") String configuredRecipients) {
        this.mailService = mailService;
        this.templateRenderer = templateRenderer;
        this.recipients = Arrays.stream(configuredRecipients.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public void notifyIfNeeded(HttpStatus status, String endpoint, Exception exception) {
        if (!shouldNotify(status, endpoint) || recipients.isEmpty()) {
            return;
        }

        String subject = "[ColaboHub] Alerta crítica en backend";
        String body = """
                Se detectó un error que requiere revisión.

                Endpoint: %s
                Estado: %s
                Error: %s

                Stacktrace:
                %s
                """.formatted(
                endpoint,
                status.value(),
                exception.getMessage(),
                toStackTrace(exception));
        String html = templateRenderer.renderHtml(status, endpoint, exception, toStackTrace(exception));

        for (String recipient : recipients) {
            try {
                mailService.sendHtml(recipient, subject, html, body);
            } catch (Exception mailException) {
                log.warn("Critical error alert email could not be sent for endpoint {}", endpoint, mailException);
            }
        }
    }

    private boolean shouldNotify(HttpStatus status, String endpoint) {
        if (status.is5xxServerError()) {
            return true;
        }

        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }

        return endpoint.startsWith("/api/pos/sales")
                || endpoint.startsWith("/api/inventory")
                || endpoint.contains("/closings");
    }

    private String toStackTrace(Exception exception) {
        StringWriter buffer = new StringWriter();
        exception.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }
}
