package com.colaborapp.pickups.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.colaborapp.pickups.domain.PickupStatus;
import com.colaborapp.pickups.web.dto.PickupResponse;

class PickupLabelPdfServiceTest {

    private final PickupLabelPdfService service = new PickupLabelPdfService();

    @Test
    void shouldGeneratePickupLabelWhenOptionalTextsAreMissing() {
        PickupResponse pickup = new PickupResponse(
                15L,
                2L,
                "Espacio Test",
                4L,
                "Butterfly",
                "RET-0000015",
                "BW2",
                null,
                "Retiro por pagar",
                true,
                new BigDecimal("7900"),
                PickupStatus.PENDING,
                null,
                null,
                null,
                Instant.parse("2026-05-12T12:00:00Z"));

        byte[] pdf = service.generateLabel(pickup);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf)).contains("%PDF-1.4");
    }
}
