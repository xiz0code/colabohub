package com.colaborapp.closings.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.closings.domain.MonthlyClosing;

public interface MonthlyClosingRepository extends JpaRepository<MonthlyClosing, Long> {

    @Query("""
            select mc
            from MonthlyClosing mc
            join fetch mc.market m
            where m.tenant.id = :tenantId
              and m.id = :marketId
              and mc.closingMonth = :closingMonth
            """)
    Optional<MonthlyClosing> findByMarketIdAndClosingMonthWithMarket(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("closingMonth") LocalDate closingMonth);
}
