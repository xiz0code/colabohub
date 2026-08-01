package com.colaborapp.promotions.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.promotions.domain.PromotionCampaign;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, Long> {

    @Query("""
            select pc from PromotionCampaign pc
            join fetch pc.store s
            join fetch pc.ownerUser owner
            where pc.tenant.id = :tenantId
              and (:ownerUserId is null or owner.id = :ownerUserId)
            order by pc.active desc, pc.name asc
            """)
    List<PromotionCampaign> findVisibleForTenant(
            @Param("tenantId") Long tenantId,
            @Param("ownerUserId") Long ownerUserId);

    @Query("""
            select pc from PromotionCampaign pc
            join fetch pc.store s
            join fetch pc.ownerUser owner
            where pc.tenant.id = :tenantId
              and (:ownerUserId is null or owner.id = :ownerUserId)
              and s.market.id in :marketIds
            order by pc.active desc, pc.name asc
            """)
    List<PromotionCampaign> findVisibleByMarketIds(
            @Param("tenantId") Long tenantId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("marketIds") List<Long> marketIds);

    @Query("""
            select pc from PromotionCampaign pc
            join fetch pc.store s
            join fetch pc.ownerUser owner
            where pc.id = :id
              and pc.tenant.id = :tenantId
            """)
    Optional<PromotionCampaign> findByIdAndTenantIdWithDetails(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
