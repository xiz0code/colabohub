package com.colaborapp.settings.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface UfProvider {

    String providerName();

    Optional<BigDecimal> fetchLatestUfValue();

    default Optional<BigDecimal> fetchUfValue(LocalDate date) {
        return Optional.empty();
    }
}
