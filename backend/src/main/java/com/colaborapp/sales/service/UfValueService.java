package com.colaborapp.sales.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.sales.repository.UfDailyValueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UfValueService {

    private final UfDailyValueRepository ufDailyValueRepository;

    @Value("${app.business-zone:America/Santiago}")
    private String businessZone;

    @Transactional(readOnly = true)
    public BigDecimal getCurrentUfValue(Long tenantId) {
        LocalDate today = LocalDate.now(ZoneId.of(businessZone));
        return ufDailyValueRepository.findByTenantIdAndEffectiveDate(tenantId, today)
                .map(value -> value.getUfValue())
                .orElseThrow(() -> new BusinessException("UF value is not configured for the current business date."));
    }
}
