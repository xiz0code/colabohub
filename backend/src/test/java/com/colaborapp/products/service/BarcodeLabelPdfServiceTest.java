package com.colaborapp.products.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.web.dto.BarcodeLabelRequest;
import com.colaborapp.products.web.dto.ProductResponse;

class BarcodeLabelPdfServiceTest {

    private final BarcodeLabelPdfService barcodeLabelPdfService = new BarcodeLabelPdfService();

    @Test
    void shouldGeneratePdfLabelsForRequestedProducts() {
        ProductResponse product = new ProductResponse(
                10L,
                5L,
                "Sakura Store",
                7L,
                "Camila",
                "Sticker BTS",
                "STK-BTS",
                "Pack coleccionable",
                new BigDecimal("2000"),
                null,
                12,
                ProductStatus.ACTIVE,
                "0000010",
                false,
                null,
                Instant.now(),
                Instant.now());

        BarcodeLabelRequest request = new BarcodeLabelRequest(
                List.of(new BarcodeLabelRequest.BarcodeLabelItemRequest(10L, 2)),
                true,
                BarcodeLabelRequest.BarcodeLabelFormat.A4);

        byte[] pdf = barcodeLabelPdfService.generateLabels(List.of(product), request);

        String header = new String(pdf, 0, 8, StandardCharsets.ISO_8859_1);
        assertThat(header).startsWith("%PDF-1.4");
        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    void shouldGenerateCompact30x20Labels() {
        ProductResponse product = new ProductResponse(
                10L,
                5L,
                "Sakura Store",
                7L,
                "Camila",
                "Photocard BTS",
                "STK-BTS",
                "Pack coleccionable",
                new BigDecimal("1000"),
                null,
                12,
                ProductStatus.ACTIVE,
                "0000010",
                false,
                null,
                Instant.now(),
                Instant.now());

        BarcodeLabelRequest request = new BarcodeLabelRequest(
                List.of(new BarcodeLabelRequest.BarcodeLabelItemRequest(10L, 1)),
                false,
                BarcodeLabelRequest.BarcodeLabelFormat.LABEL_30X20);

        byte[] pdf = barcodeLabelPdfService.generateLabels(List.of(product), request);

        String header = new String(pdf, 0, 8, StandardCharsets.ISO_8859_1);
        assertThat(header).startsWith("%PDF-1.4");
        assertThat(pdf.length).isGreaterThan(300);
    }
}
