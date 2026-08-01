package com.colaborapp.promotions.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.promotions.domain.ProductPromotion;

public interface ProductPromotionRepository extends JpaRepository<ProductPromotion, Long> {

    Optional<ProductPromotion> findFirstByProductIdOrderByIdAsc(Long productId);

    Optional<ProductPromotion> findByPromotionCampaignIdAndProductId(Long promotionCampaignId, Long productId);

    @Query("""
            select pp from ProductPromotion pp
            where pp.product.id = :productId
              and pp.active = true
              and (pp.startsAt is null or pp.startsAt <= :referenceTime)
              and (pp.endsAt is null or pp.endsAt >= :referenceTime)
            order by pp.id asc
            """)
    Optional<ProductPromotion> findFirstActiveByProductId(
            @Param("productId") Long productId,
            @Param("referenceTime") Instant referenceTime);

    void deleteByProductId(Long productId);

    @Query("""
            select pp from ProductPromotion pp
            join fetch pp.product p
            left join fetch pp.promotionCampaign pc
            where p.id in :productIds
              and pp.active = true
              and (pp.startsAt is null or pp.startsAt <= :referenceTime)
              and (pp.endsAt is null or pp.endsAt >= :referenceTime)
            order by
              case when pp.type = com.colaborapp.promotions.domain.PromotionType.QUANTITY_BLOCK then 0 else 1 end,
              pp.blockQuantity desc,
              pp.blockPrice asc,
              pp.id asc
            """)
    List<ProductPromotion> findActiveByProductIds(
            @Param("productIds") List<Long> productIds,
            @Param("referenceTime") Instant referenceTime);

    @Query("""
            select pp from ProductPromotion pp
            join fetch pp.product p
            where pp.promotionCampaign.id = :promotionCampaignId
            order by p.name asc
            """)
    List<ProductPromotion> findAllByPromotionCampaignIdOrderByProductNameAsc(@Param("promotionCampaignId") Long promotionCampaignId);

    long countByPromotionCampaignId(Long promotionCampaignId);

    void deleteByPromotionCampaignId(Long promotionCampaignId);
}
