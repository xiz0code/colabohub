package com.colaborapp.sales.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleStatus;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Sale> findFirstByTenantIdAndStatusOrderByOpenedAtDesc(Long tenantId, SaleStatus status);

    Optional<Sale> findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(Long tenantId, Long marketId, SaleStatus status);

    Optional<Sale> findFirstByTenantIdAndMarketIsNullAndStatusOrderByOpenedAtDesc(Long tenantId, SaleStatus status);

    List<Sale> findAllByTenantIdAndStatusAndConfirmedAtGreaterThanEqualAndConfirmedAtLessThanOrderByConfirmedAtDescIdDesc(
            Long tenantId,
            SaleStatus status,
            Instant startAt,
            Instant endAt);

    Optional<Sale> findFirstBySaleNumberStartingWithOrderBySaleNumberDesc(String prefix);

    @Query("""
            select count(distinct s.id), sum(sss.subtotalAmount), sum(sss.subtotalAmount), sum(sss.totalCommissionAmount), sum(sss.netAmount)
            from SaleStoreSummary sss
            join sss.sale s
            where s.tenant.id = :tenantId
              and s.status = :status
              and s.confirmedAt >= :startAt
              and s.confirmedAt < :endAt
            """)
    Object[] summarizeByPeriod(
            @Param("tenantId") Long tenantId,
            @Param("status") SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
            select count(distinct s.id), sum(sss.subtotalAmount), sum(sss.totalCommissionAmount), sum(sss.netAmount)
            from SaleStoreSummary sss
            join sss.sale s
            where s.tenant.id = :tenantId
              and s.status = :status
              and sss.store.id = :storeId
            """)
    Object[] summarizeStore(
            @Param("tenantId") Long tenantId,
            @Param("storeId") Long storeId,
            @Param("status") SaleStatus status);

    @Query("""
            select s.id, s.saleNumber, s.confirmedAt, sss.lineCount, sss.unitCount, sss.subtotalAmount, sss.totalCommissionAmount, sss.netAmount
            from SaleStoreSummary sss
            join sss.sale s
            where s.tenant.id = :tenantId
              and s.status = :status
              and sss.store.id = :storeId
            order by s.confirmedAt desc, s.id desc
            """)
    List<Object[]> findRecentStoreSales(
            @Param("tenantId") Long tenantId,
            @Param("storeId") Long storeId,
            @Param("status") SaleStatus status,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            select sss.store.id, sss.store.name, count(distinct s.id), sum(sss.subtotalAmount), sum(sss.totalCommissionAmount), sum(sss.netAmount)
            from SaleStoreSummary sss
            join sss.sale s
            where s.tenant.id = :tenantId
              and s.status = :status
              and s.confirmedAt >= :startAt
              and s.confirmedAt < :endAt
            group by sss.store.id, sss.store.name
            order by sss.store.name asc
            """)
    List<Object[]> summarizeStoresByPeriod(
            @Param("tenantId") Long tenantId,
            @Param("status") SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
            select sum(sss.subtotalAmount), sum(sss.unitCount)
            from SaleStoreSummary sss
            join sss.sale s
            where s.tenant.id = :tenantId
              and s.market.id = :marketId
              and s.status = :status
              and s.confirmedAt >= :startAt
              and s.confirmedAt < :endAt
            """)
    Object[] summarizeMarketByPeriod(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("status") SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
            select sss.store.id, sss.store.name, sum(sss.subtotalAmount), sum(sss.unitCount)
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
    List<Object[]> summarizeMarketStoresByPeriod(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("status") SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
            select sss.store.id, sss.store.name, count(distinct s.id), sum(sss.subtotalAmount), sum(sss.totalCommissionAmount), sum(sss.netAmount)
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
    List<Object[]> summarizeMarketPayoutsByPeriod(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("status") SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);
}
