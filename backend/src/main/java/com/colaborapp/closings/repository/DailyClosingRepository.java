package com.colaborapp.closings.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.closings.domain.DailyClosing;

public interface DailyClosingRepository extends JpaRepository<DailyClosing, Long> {

    @Query("""
            select dc
            from DailyClosing dc
            join fetch dc.market m
            where m.tenant.id = :tenantId
              and m.id = :marketId
              and dc.closingDate = :closingDate
            """)
    Optional<DailyClosing> findByMarketIdAndClosingDateWithMarket(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("closingDate") LocalDate closingDate);
}
