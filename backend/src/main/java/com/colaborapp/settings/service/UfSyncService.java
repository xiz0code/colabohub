package com.colaborapp.settings.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UfSyncService {

    private static final Logger log = LoggerFactory.getLogger(UfSyncService.class);

    private final UfExternalService ufExternalService;
    private final MarketRepository marketRepository;

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Santiago")
    public void scheduledDailyUfSync() {
        try {
            syncDailyUf();
        } catch (RuntimeException exception) {
            log.warn("No pudimos sincronizar la UF automatica para las Tiendas activas.", exception);
        }
    }

    @Transactional
    public void syncDailyUf() {
        var ufResult = ufExternalService.fetchLatestUfValueWithSource();
        var updatedAt = Instant.now();

        updateMarketsWithUf(
                marketRepository.findByActiveTrueOrderByNameAsc().stream()
                        .filter(market -> !market.isUfManualOverride())
                        .toList(),
                ufResult.value(),
                updatedAt,
                ufResult.providerName());
    }

    @Transactional
    public java.math.BigDecimal syncMarketUf(Market market) {
        var ufResult = ufExternalService.fetchLatestUfValueWithSource();
        updateMarketsWithUf(List.of(market), ufResult.value(), Instant.now(), ufResult.providerName());
        return ufResult.value();
    }

    private void updateMarketsWithUf(List<Market> markets, java.math.BigDecimal value, Instant updatedAt, String providerName) {
        for (var market : markets) {
            market.setUfValue(value);
            market.setUfUpdatedAt(updatedAt);
            log.info("UF updated for market {} using provider {}", market.getId(), providerName);
        }
    }
}
