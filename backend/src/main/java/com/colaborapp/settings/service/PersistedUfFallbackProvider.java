package com.colaborapp.settings.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.sales.repository.UfDailyValueRepository;

import lombok.RequiredArgsConstructor;

@Component
@Order(100)
@RequiredArgsConstructor
public class PersistedUfFallbackProvider implements UfProvider {

    private final UfDailyValueRepository ufDailyValueRepository;
    private final MarketRepository marketRepository;

    @Override
    public String providerName() {
        return "persisted-last-known-uf";
    }

    @Override
    public Optional<BigDecimal> fetchLatestUfValue() {
        Optional<BigDecimal> latestDailyValue = ufDailyValueRepository.findTopByOrderByEffectiveDateDescIdDesc()
                .map(ufDailyValue -> ufDailyValue.getUfValue())
                .filter(this::isValidPositiveValue);
        if (latestDailyValue.isPresent()) {
            return latestDailyValue;
        }

        return marketRepository.findFirstByUfValueIsNotNullOrderByUfUpdatedAtDescIdDesc()
                .map(market -> market.getUfValue())
                .filter(this::isValidPositiveValue);
    }

    private boolean isValidPositiveValue(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
