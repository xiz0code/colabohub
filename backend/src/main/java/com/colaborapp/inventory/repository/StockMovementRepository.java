package com.colaborapp.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.inventory.domain.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);
}
