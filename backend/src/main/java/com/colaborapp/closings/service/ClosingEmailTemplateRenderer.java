package com.colaborapp.closings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ClosingEmailTemplateRenderer {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public String renderCollaboratorDailyHtml(
            String marketName,
            LocalDate closingDate,
            CollaboratorSalesSummaryService.CollaboratorSummary collaborator) {
        String hero = "Resumen diario";
        String subtitle = marketName + " - " + DATE_FORMATTER.format(closingDate);
        String intro = "Aquí tienes el consolidado de tu jornada.";
        String chips = metricGrid(List.of(
                metric("Ventas", formatMoney(collaborator.totalSalesAmount()), "#f5efff"),
                metric("Comisiones", formatMoney(collaborator.totalCommissionAmount()), "#fff1f7"),
                metric("Neto", formatMoney(collaborator.totalNetAmount()), "#eefcf7"),
                metric("IVA total", formatMoney(collaborator.totalIvaAmount()), "#fff6eb"),
                metric("IVA a pagar", formatMoney(collaborator.ivaToPayAmount()), "#fff4ef"),
                metric("Items", String.valueOf(collaborator.totalItems()), "#f3f7ff")));
        String table = productTable(collaborator.products());
        return layout(hero, subtitle, collaborator.collaboratorName(), intro, chips, table);
    }

    public String renderCollaboratorMonthlyHtml(
            String marketName,
            LocalDate closingMonth,
            CollaboratorSalesSummaryService.CollaboratorSummary collaborator) {
        String hero = "Resumen mensual";
        String subtitle = marketName + " - " + closingMonth.getYear() + "-" + String.format("%02d", closingMonth.getMonthValue());
        String intro = "Este es tu consolidado mensual listo para revisión comercial.";
        String chips = metricGrid(List.of(
                metric("Ventas del mes", formatMoney(collaborator.totalSalesAmount()), "#f5efff"),
                metric("Comisiones", formatMoney(collaborator.totalCommissionAmount()), "#fff1f7"),
                metric("Neto", formatMoney(collaborator.totalNetAmount()), "#eefcf7"),
                metric("IVA total", formatMoney(collaborator.totalIvaAmount()), "#fff6eb"),
                metric("IVA a pagar", formatMoney(collaborator.ivaToPayAmount()), "#fff4ef"),
                metric("Factura", collaborator.factura() ? "Si" : "No", "#f3f7ff")));
        String table = productTable(collaborator.products());
        return layout(hero, subtitle, collaborator.collaboratorName(), intro, chips, table);
    }

    public String renderDailyAdminHtml(com.colaborapp.closings.web.dto.DailyClosingResponse response) {
        String rows = response.stores().isEmpty()
                ? "<tr><td colspan='5' style='padding:14px 16px;color:#756f86;'>No hubo Tiendas con ventas en este cierre.</td></tr>"
                : response.stores().stream()
                        .map(store -> "<tr>"
                                + cell(store.storeName(), true)
                                + cell(String.valueOf(store.saleCount()), false)
                                + cell(String.valueOf(store.totalItems()), false)
                                + cell(formatMoney(store.totalCommissionAmount()), false)
                                + cell(formatMoney(store.totalNetAmount()), false)
                                + "</tr>")
                        .reduce("", String::concat);

        String table = """
                <div style="margin-top:24px;border:1px solid #eee7fb;border-radius:20px;overflow:hidden;background:#fff;">
                  <div style="padding:18px 20px 8px;font-size:16px;font-weight:700;color:#302b44;">Desglose por Tienda</div>
                  <table style="width:100%%;border-collapse:collapse;font-size:14px;">
                    <thead>
                      <tr style="background:linear-gradient(135deg,#f7eefc,#eef4ff);color:#6f6887;text-align:left;">
                        <th style="padding:12px 16px;">Tienda</th>
                        <th style="padding:12px 16px;">Ventas</th>
                        <th style="padding:12px 16px;">Items</th>
                        <th style="padding:12px 16px;">Comisión</th>
                        <th style="padding:12px 16px;">Neto</th>
                      </tr>
                    </thead>
                    <tbody>%s</tbody>
                  </table>
                </div>
                """.formatted(rows);

        String chips = metricGrid(List.of(
                metric("Ventas", formatMoney(response.totalSalesAmount()), "#f5efff"),
                metric("Comisiones", formatMoney(response.totalCommissionAmount()), "#fff1f7"),
                metric("Neto", formatMoney(response.totalNetAmount()), "#eefcf7"),
                metric("Transacciones", String.valueOf(response.saleCount()), "#f3f7ff")));
        return layout("Cierre diario del Espacio",
                response.marketName() + " - " + DATE_FORMATTER.format(response.closingDate()),
                response.marketName(),
                "Resumen consolidado del cierre diario generado por ColaboHub.",
                chips,
                table);
    }

    private String layout(String hero, String subtitle, String greetingName, String intro, String metrics, String mainContent) {
        return """
                <div style="margin:0;padding:24px;background:#f7f4fb;font-family:'Segoe UI',Arial,sans-serif;color:#2f2a44;">
                  <div style="max-width:760px;margin:0 auto;background:#ffffff;border-radius:28px;padding:28px;box-shadow:0 18px 45px rgba(153,132,192,0.14);">
                    <div style="padding:22px 24px;border-radius:24px;background:linear-gradient(135deg,#f5ecff 0%%,#fff4f8 52%%,#eef8ff 100%%);border:1px solid #f0e7fb;">
                      <div style="font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:#a04fd0;font-weight:700;">ColaboHub</div>
                      <h1 style="margin:12px 0 6px;font-size:30px;line-height:1.15;color:#2f2a44;">%s</h1>
                      <div style="font-size:14px;color:#6f6887;">%s</div>
                    </div>
                    <div style="padding:26px 6px 8px;">
                      <div style="font-size:20px;font-weight:700;color:#2f2a44;">Hola, %s</div>
                      <p style="margin:10px 0 0;font-size:15px;line-height:1.7;color:#6f6887;">%s</p>
                    </div>
                    %s
                    %s
                    <div style="margin-top:24px;padding:18px 20px;border-radius:20px;background:#faf7ff;color:#746d88;font-size:13px;line-height:1.7;">
                      Este resumen fue generado automáticamente por ColaboHub para apoyar la operación diaria de tu Espacio.
                    </div>
                  </div>
                </div>
                """.formatted(hero, subtitle, escapeHtml(greetingName), escapeHtml(intro), metrics, mainContent);
    }

    private String metricGrid(List<String> metrics) {
        return "<div style='display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:24px;'>"
                + String.join("", metrics)
                + "</div>";
    }

    private String metric(String label, String value, String background) {
        return """
                <div style="padding:16px 18px;border-radius:18px;background:%s;border:1px solid #f0e7fb;">
                  <div style="font-size:12px;color:#7a738d;text-transform:uppercase;letter-spacing:.08em;">%s</div>
                  <div style="margin-top:8px;font-size:24px;font-weight:700;color:#2f2a44;">%s</div>
                </div>
                """.formatted(background, escapeHtml(label), escapeHtml(value));
    }

    private String productTable(List<CollaboratorSalesSummaryService.CollaboratorProductSummary> products) {
        String rows = products == null || products.isEmpty()
                ? "<tr><td colspan='6' style='padding:14px 16px;color:#756f86;'>No hubo productos vendidos en este periodo.</td></tr>"
                : products.stream()
                        .map(product -> "<tr>"
                                + cell(product.productName(), true)
                                + cell(product.productSku() == null || product.productSku().isBlank() ? "Sin SKU" : product.productSku(), false)
                                + cell(String.valueOf(product.totalQuantity()), false)
                                + cell(formatMoney(product.totalSalesAmount()), false)
                                + cell(formatMoney(product.totalCommissionAmount()), false)
                                + cell(formatMoney(product.totalNetAmount()), false)
                                + "</tr>")
                        .reduce("", String::concat);

        return """
                <div style="margin-top:24px;border:1px solid #eee7fb;border-radius:20px;overflow:hidden;background:#fff;">
                  <div style="padding:18px 20px 8px;font-size:16px;font-weight:700;color:#302b44;">Detalle de productos</div>
                  <table style="width:100%%;border-collapse:collapse;font-size:14px;">
                    <thead>
                      <tr style="background:linear-gradient(135deg,#f7eefc,#eef4ff);color:#6f6887;text-align:left;">
                        <th style="padding:12px 16px;">Producto</th>
                        <th style="padding:12px 16px;">SKU</th>
                        <th style="padding:12px 16px;">Cant.</th>
                        <th style="padding:12px 16px;">Ventas</th>
                        <th style="padding:12px 16px;">Comisión</th>
                        <th style="padding:12px 16px;">Neto</th>
                      </tr>
                    </thead>
                    <tbody>%s</tbody>
                  </table>
                </div>
                """.formatted(rows);
    }

    private String cell(String value, boolean strong) {
        return "<td style='padding:12px 16px;border-top:1px solid #f4effa;color:#3d3753;"
                + (strong ? "font-weight:600;" : "")
                + "'>" + escapeHtml(value) + "</td>";
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.HALF_UP);
        return "$" + safe.toPlainString().replaceAll("\\B(?=(\\d{3})+(?!\\d))", ".");
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
