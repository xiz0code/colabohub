package com.colaborapp.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.colaborapp.common.exception.ResourceNotFoundException;

class UfExternalServiceTest {

    @Test
    void shouldUsePrimaryProviderWhenAvailable() {
        UfExternalService service = new UfExternalService(List.of(
                provider("mindicador.cl", Optional.of(new BigDecimal("39000.00"))),
                provider("persisted-last-known-uf", Optional.of(new BigDecimal("38500.00")))));

        UfExternalService.UfValueResult result = service.fetchLatestUfValueWithSource();

        assertThat(result.value()).isEqualByComparingTo("39000.00");
        assertThat(result.providerName()).isEqualTo("mindicador.cl");
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void shouldFallbackToSecondaryProviderWhenPrimaryFails() {
        UfProvider failingProvider = new UfProvider() {
            @Override
            public String providerName() {
                return "mindicador.cl";
            }

            @Override
            public Optional<BigDecimal> fetchLatestUfValue() {
                throw new RuntimeException("timeout");
            }
        };

        UfExternalService service = new UfExternalService(List.of(
                failingProvider,
                provider("persisted-last-known-uf", Optional.of(new BigDecimal("38500.00")))));

        UfExternalService.UfValueResult result = service.fetchLatestUfValueWithSource();

        assertThat(result.value()).isEqualByComparingTo("38500.00");
        assertThat(result.providerName()).isEqualTo("persisted-last-known-uf");
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void shouldFailWhenNoUfProviderReturnsAUsableValue() {
        UfExternalService service = new UfExternalService(List.of(
                provider("mindicador.cl", Optional.empty()),
                provider("persisted-last-known-uf", Optional.empty())));

        assertThatThrownBy(service::fetchLatestUfValueWithSource)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No pudimos obtener la UF actual desde las fuentes configuradas.");
    }

    private UfProvider provider(String name, Optional<BigDecimal> value) {
        return new UfProvider() {
            @Override
            public String providerName() {
                return name;
            }

            @Override
            public Optional<BigDecimal> fetchLatestUfValue() {
                return value;
            }
        };
    }
}
