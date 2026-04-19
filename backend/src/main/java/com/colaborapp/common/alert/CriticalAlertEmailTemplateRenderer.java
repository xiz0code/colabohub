package com.colaborapp.common.alert;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CriticalAlertEmailTemplateRenderer {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final String businessZone;

    public CriticalAlertEmailTemplateRenderer(@Value("${app.business-zone:America/Santiago}") String businessZone) {
        this.businessZone = businessZone;
    }

    public String renderHtml(HttpStatus status, String endpoint, Exception exception, String stackTrace) {
        String severity = status.is5xxServerError() ? "Alta" : "Media";
        String headline = status.is5xxServerError() ? "Se requiere revisión inmediata" : "Se detectó una incidencia operativa";
        String background = status.is5xxServerError()
                ? "linear-gradient(135deg,#fff1f3 0%,#fff6ef 48%,#fffdf7 100%)"
                : "linear-gradient(135deg,#fff8ef 0%,#fff6f5 48%,#fffdf7 100%)";

        return """
                <div style="margin:0;padding:24px;background:#f7f4fb;font-family:'Segoe UI',Arial,sans-serif;color:#2f2a44;">
                  <div style="max-width:760px;margin:0 auto;background:#ffffff;border-radius:28px;padding:28px;box-shadow:0 18px 45px rgba(153,132,192,0.14);">
                    <div style="padding:22px 24px;border-radius:24px;background:%s;border:1px solid #f0e7fb;">
                      <div style="font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:#a04fd0;font-weight:700;">ColaboHub</div>
                      <h1 style="margin:12px 0 6px;font-size:30px;line-height:1.15;color:#2f2a44;">Alerta crítica del sistema</h1>
                      <div style="font-size:14px;color:#6f6887;">%s</div>
                    </div>

                    <div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:24px;">
                      %s
                      %s
                      %s
                      %s
                    </div>

                    <div style="margin-top:24px;padding:18px 20px;border-radius:20px;background:#fff8fb;border:1px solid #f2e4f0;">
                      <div style="font-size:12px;color:#8d6f81;text-transform:uppercase;letter-spacing:.08em;">Error detectado</div>
                      <div style="margin-top:10px;font-size:20px;font-weight:700;color:#2f2a44;">%s</div>
                    </div>

                    <div style="margin-top:24px;border:1px solid #eee7fb;border-radius:20px;overflow:hidden;background:#fff;">
                      <div style="padding:18px 20px 8px;font-size:16px;font-weight:700;color:#302b44;">Stacktrace resumido</div>
                      <div style="padding:0 20px 20px;">
                        <pre style="margin:0;padding:16px;border-radius:16px;background:#2b2338;color:#f7efff;font-size:12px;line-height:1.65;white-space:pre-wrap;word-break:break-word;">%s</pre>
                      </div>
                    </div>

                    <div style="margin-top:24px;padding:18px 20px;border-radius:20px;background:#faf7ff;color:#746d88;font-size:13px;line-height:1.7;">
                      Correo generado automáticamente por ColaboHub para monitoreo operativo del backend.
                    </div>
                  </div>
                </div>
                """.formatted(
                background,
                escapeHtml(headline),
                metric("Endpoint", endpoint == null || endpoint.isBlank() ? "Sin endpoint" : endpoint),
                metric("Estado", String.valueOf(status.value())),
                metric("Fecha", DATE_TIME_FORMATTER.format(ZonedDateTime.now(java.time.ZoneId.of(businessZone)))),
                metric("Severidad", severity),
                escapeHtml(exception.getMessage() == null || exception.getMessage().isBlank() ? "Error sin mensaje" : exception.getMessage()),
                escapeHtml(limitStackTrace(stackTrace)));
    }

    private String metric(String label, String value) {
        return """
                <div style="padding:16px 18px;border-radius:18px;background:#f7f2ff;border:1px solid #f0e7fb;">
                  <div style="font-size:12px;color:#7a738d;text-transform:uppercase;letter-spacing:.08em;">%s</div>
                  <div style="margin-top:8px;font-size:16px;font-weight:700;color:#2f2a44;">%s</div>
                </div>
                """.formatted(escapeHtml(label), escapeHtml(value));
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String limitStackTrace(String stackTrace) {
        if (stackTrace == null) {
            return "";
        }
        String[] lines = stackTrace.split("\\R");
        int maxLines = Math.min(lines.length, 18);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < maxLines; index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines[index]);
        }
        if (lines.length > maxLines) {
            builder.append("\n...");
        }
        return builder.toString();
    }
}
