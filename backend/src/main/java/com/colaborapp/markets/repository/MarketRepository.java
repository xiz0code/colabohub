package com.colaborapp.markets.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.markets.domain.Market;

public interface MarketRepository extends JpaRepository<Market, Long> {

    List<Market> findByTenantIdOrderByNameAsc(Long tenantId);

    List<Market> findByTenantIdAndIdInOrderByNameAsc(Long tenantId, List<Long> ids);

    List<Market> findByActiveTrueOrderByNameAsc();

    Optional<Market> findFirstByUfValueIsNotNullOrderByUfUpdatedAtDescIdDesc();

    Optional<Market> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(Long tenantId, String name, Long id);
}
