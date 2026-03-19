package com.colaborapp.settings.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.colaborapp.common.exception.ResourceNotFoundException;

@Service
public class UfExternalService {

    private static final Logger log = LoggerFactory.getLogger(UfExternalService.class);

    private final List<UfProvider> providers;

    public UfExternalService(List<UfProvider> providers) {
        this.providers = providers;
    }

    public BigDecimal fetchLatestUfValue() {
        return fetchLatestUfValueWithSource().value();
    }

    public UfValueResult fetchLatestUfValueWithSource() {
        RuntimeException lastFailure = null;
        for (int index = 0; index < providers.size(); index++) {
            UfProvider provider = providers.get(index);
            try {
                var value = provider.fetchLatestUfValue();
                if (value.isPresent() && value.get().compareTo(BigDecimal.ZERO) > 0) {
                    boolean fallbackUsed = index > 0;
                    if (fallbackUsed) {
                        log.warn("UF fallback in use. Provider selected: {}", provider.providerName());
                    } else {
                        log.info("UF provider principal OK: {}", provider.providerName());
                    }
                    return new UfValueResult(value.get(), provider.providerName(), fallbackUsed);
                }
                log.warn("UF provider returned no usable value: {}", provider.providerName());
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("UF provider failed: {}", provider.providerName(), exception);
            }
        }

        throw new ResourceNotFoundException(
                lastFailure == null
                        ? "No pudimos obtener la UF actual desde las fuentes configuradas."
                        : "No pudimos obtener la UF actual desde las fuentes configuradas.");
    }

    public record UfValueResult(
            BigDecimal value,
            String providerName,
            boolean fallbackUsed) {
    }
}
