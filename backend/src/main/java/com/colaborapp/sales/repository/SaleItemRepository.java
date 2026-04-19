package com.colaborapp.sales.repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.sales.domain.SaleItem;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query("""
            select si from SaleItem si
            left join fetch si.product p
            join fetch si.store s
            join fetch s.market market
            where si.sale.id = :saleId
            order by si.createdAt asc, si.id asc
            """)
    List<SaleItem> findAllBySaleIdWithDetails(@Param("saleId") Long saleId);

    @Query("""
            select si from SaleItem si
            left join fetch si.product p
            join fetch si.store s
            join fetch s.market market
            where si.sale.id in :saleIds
            order by si.sale.id asc, si.createdAt asc, si.id asc
            """)
    List<SaleItem> findAllBySaleIdInWithDetails(@Param("saleIds") List<Long> saleIds);

    Optional<SaleItem> findByIdAndSaleId(Long id, Long saleId);

    Optional<SaleItem> findBySaleIdAndProductId(Long saleId, Long productId);

    Optional<SaleItem> findBySaleIdAndManualReference(Long saleId, String manualReference);

    @Query("""
            select si from SaleItem si
            join fetch si.sale sale
            left join fetch si.product product
            join fetch si.store store
            join fetch store.market market
            where sale.tenant.id = :tenantId
              and market.id = :marketId
              and sale.status = :status
              and sale.confirmedAt >= :startAt
              and sale.confirmedAt < :endAt
            order by sale.confirmedAt asc, si.id asc
            """)
    List<SaleItem> findAllByMarketIdAndPeriodWithDetails(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("status") com.colaborapp.sales.domain.SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Query("""
            select si from SaleItem si
            join fetch si.sale sale
            left join fetch si.product product
            join fetch si.store store
            join fetch store.market market
            where sale.tenant.id = :tenantId
              and si.collaboratorUserId = :collaboratorUserId
              and sale.status = :status
              and sale.confirmedAt >= :startAt
              and sale.confirmedAt < :endAt
            order by sale.confirmedAt asc, si.id asc
            """)
    List<SaleItem> findAllByCollaboratorAndPeriodWithDetails(
            @Param("tenantId") Long tenantId,
            @Param("collaboratorUserId") Long collaboratorUserId,
            @Param("status") com.colaborapp.sales.domain.SaleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);
}
