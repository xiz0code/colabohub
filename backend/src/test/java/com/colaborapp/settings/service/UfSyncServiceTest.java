package com.colaborapp.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;

@ExtendWith(MockitoExtension.class)
class UfSyncServiceTest {

    @Mock
    private UfExternalService ufExternalService;

    @Mock
    private MarketRepository marketRepository;

    @InjectMocks
    private UfSyncService ufSyncService;

    @Test
    void shouldUpdateOnlyAutomaticActiveMarkets() {
        Market automaticMarket = new Market();
        automaticMarket.setId(1L);
        automaticMarket.setName("Sakura Store");
        automaticMarket.setUfManualOverride(false);

        Market manualMarket = new Market();
        manualMarket.setId(2L);
        manualMarket.setName("Momo Store");
        manualMarket.setUfManualOverride(true);
        manualMarket.setUfValue(new BigDecimal("34000.00"));
        manualMarket.setUfUpdatedAt(Instant.parse("2026-03-15T10:00:00Z"));

        when(ufExternalService.fetchLatestUfValueWithSource())
                .thenReturn(new UfExternalService.UfValueResult(new BigDecimal("38200.45"), "mindicador.cl", false));
        when(marketRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(automaticMarket, manualMarket));

        ufSyncService.syncDailyUf();

        assertThat(automaticMarket.getUfValue()).isEqualByComparingTo("38200.45");
        assertThat(automaticMarket.getUfUpdatedAt()).isNotNull();
        assertThat(manualMarket.getUfValue()).isEqualByComparingTo("34000.00");
        assertThat(manualMarket.getUfUpdatedAt()).isEqualTo(Instant.parse("2026-03-15T10:00:00Z"));
        verify(marketRepository).findByActiveTrueOrderByNameAsc();
    }
}
