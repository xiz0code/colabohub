package com.colaborapp.products.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.products.domain.ProductAuditLog;

public interface ProductAuditLogRepository extends JpaRepository<ProductAuditLog, Long> {

    List<ProductAuditLog> findByProductIdOrderByCreatedAtDesc(Long productId);
}
