package com.colaborapp.sales.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.sales.domain.UfDailyValue;

public interface UfDailyValueRepository extends JpaRepository<UfDailyValue, Long> {

    Optional<UfDailyValue> findByTenantIdAndEffectiveDate(Long tenantId, LocalDate effectiveDate);

    Optional<UfDailyValue> findTopByOrderByEffectiveDateDescIdDesc();
}
