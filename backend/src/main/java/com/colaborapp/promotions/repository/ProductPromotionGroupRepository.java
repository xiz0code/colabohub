package com.colaborapp.promotions.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.promotions.domain.ProductPromotionGroup;

public interface ProductPromotionGroupRepository extends JpaRepository<ProductPromotionGroup, Long> {

    List<ProductPromotionGroup> findAllByTenantIdAndStoreIdAndOwnerUserIdOrderByNameAsc(Long tenantId, Long storeId, Long ownerUserId);

    Optional<ProductPromotionGroup> findByIdAndTenantId(Long id, Long tenantId);

    Optional<ProductPromotionGroup> findByTenantIdAndStoreIdAndOwnerUserIdAndNameIgnoreCase(Long tenantId, Long storeId, Long ownerUserId, String name);
}
