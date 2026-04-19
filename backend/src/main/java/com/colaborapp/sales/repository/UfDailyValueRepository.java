package com.colaborapp.sales.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.sales.domain.UfDailyValue;

public interface UfDailyValueRepository extends JpaRepository<UfDailyValue, Long> {

    Optional<UfDailyValue> findByTenantIdAndEffectiveDate(Long tenantId, LocalDate effectiveDate);

    Optional<UfDailyValue> findTopByTenantIdOrderByEffectiveDateDescIdDesc(Long tenantId);

    Optional<UfDailyValue> findTopByOrderByEffectiveDateDescIdDesc();

    List<UfDailyValue> findAllByTenantIdAndEffectiveDateBetween(Long tenantId, LocalDate dateFrom, LocalDate dateTo);
}
