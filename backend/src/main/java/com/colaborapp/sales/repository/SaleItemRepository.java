package com.colaborapp.sales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.sales.domain.SaleItem;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query("""
            select si from SaleItem si
            join fetch si.product p
            join fetch si.store s
            where si.sale.id = :saleId
            order by si.createdAt asc, si.id asc
            """)
    List<SaleItem> findAllBySaleIdWithDetails(@Param("saleId") Long saleId);

    @Query("""
            select si from SaleItem si
            join fetch si.product p
            join fetch si.store s
            where si.sale.id in :saleIds
            order by si.sale.id asc, si.createdAt asc, si.id asc
            """)
    List<SaleItem> findAllBySaleIdInWithDetails(@Param("saleIds") List<Long> saleIds);

    Optional<SaleItem> findByIdAndSaleId(Long id, Long saleId);

    Optional<SaleItem> findBySaleIdAndProductId(Long saleId, Long productId);
}
