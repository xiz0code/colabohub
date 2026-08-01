package com.colaborapp.inventory.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.inventory.domain.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    @Query("""
            select sm.product.id as productId, sum(sm.quantity) as quantity
            from StockMovement sm
            where sm.product.id in :productIds
              and sm.createdAt >= :since
              and sm.quantity > 0
            group by sm.product.id
            """)
    List<ProductStockIncreaseSummary> sumPositiveQuantityByProductIdsSince(
            @Param("productIds") List<Long> productIds,
            @Param("since") Instant since);

    interface ProductStockIncreaseSummary {
        Long getProductId();

        Long getQuantity();
    }
}
