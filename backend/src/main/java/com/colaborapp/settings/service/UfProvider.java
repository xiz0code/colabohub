package com.colaborapp.settings.service;

import java.math.BigDecimal;
import java.util.Optional;

public interface UfProvider {

    String providerName();

    Optional<BigDecimal> fetchLatestUfValue();
}
