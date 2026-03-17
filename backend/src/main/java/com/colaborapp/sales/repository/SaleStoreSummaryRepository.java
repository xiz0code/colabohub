package com.colaborapp.sales.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.sales.domain.SaleStoreSummary;

public interface SaleStoreSummaryRepository extends JpaRepository<SaleStoreSummary, Long> {

    @Query("""
            select sss from SaleStoreSummary sss
            join fetch sss.store s
            where sss.sale.id = :saleId
            order by s.name asc
            """)
    List<SaleStoreSummary> findAllBySaleIdWithStore(@Param("saleId") Long saleId);

    Optional<SaleStoreSummary> findBySaleIdAndStoreId(Long saleId, Long storeId);

    @Query("""
            select sss from SaleStoreSummary sss
            join fetch sss.sale sale
            join fetch sss.store store
            join fetch store.market market
            where sale.tenant.id = :tenantId
              and sale.status = :status
              and sale.confirmedAt >= :startAt
              and sale.confirmedAt < :endAt
            order by sale.confirmedAt desc, sale.id desc, store.name asc
            """)
    List<SaleStoreSummary> findByPeriodWithSaleAndStore(
            @Param("tenantId") Long tenantId,
            @Param("status") com.colaborapp.sales.domain.SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
            select count(distinct s.id), sum(sss.subtotalAmount), sum(sss.totalCommissionAmount), sum(sss.netAmount)
            from SaleStoreSummary sss
            join sss.sale s
            where s.tenant.id = :tenantId
              and s.market.id = :marketId
              and s.status = :status
              and s.confirmedAt >= :startAt
              and s.confirmedAt < :endAt
            """)
    Object[] summarizeClosingTotals(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("status") com.colaborapp.sales.domain.SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
            select sss.store.id, sss.store.name, count(distinct s.id), sum(sss.subtotalAmount), sum(sss.totalCommissionAmount), sum(sss.netAmount), sum(sss.unitCount)
            from SaleStoreSummary sss
            join sss.sale s
            where s.tenant.id = :tenantId
              and s.market.id = :marketId
              and s.status = :status
              and s.confirmedAt >= :startAt
              and s.confirmedAt < :endAt
            group by sss.store.id, sss.store.name
            order by sss.store.name asc
            """)
    List<Object[]> summarizeClosingStores(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("status") com.colaborapp.sales.domain.SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    void deleteBySaleId(Long saleId);
}
