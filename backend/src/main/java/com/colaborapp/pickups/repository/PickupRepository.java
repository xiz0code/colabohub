package com.colaborapp.pickups.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.pickups.domain.Pickup;
import com.colaborapp.pickups.domain.PickupStatus;

public interface PickupRepository extends JpaRepository<Pickup, Long> {

    @Query("""
            select p from Pickup p
            join fetch p.store s
            join fetch p.market m
            left join fetch p.linkedSale sale
            where p.tenant.id = :tenantId
              and (:marketId is null or m.id = :marketId)
              and (:storeIdsEmpty = true or s.id in :storeIds)
              and (:collaboratorUserId is null or p.collaboratorUserId = :collaboratorUserId)
              and (:status is null or p.status = :status)
              and (
                  :query is null
                  or lower(p.pickupNumber) like :query
                  or lower(p.customerName) like :query
                  or lower(p.description) like :query
              )
            order by p.createdAt desc, p.id desc
            """)
    List<Pickup> search(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("storeIds") List<Long> storeIds,
            @Param("storeIdsEmpty") boolean storeIdsEmpty,
            @Param("collaboratorUserId") Long collaboratorUserId,
            @Param("status") PickupStatus status,
            @Param("query") String query);

    @Query("""
            select p from Pickup p
            join fetch p.store s
            join fetch p.market m
            left join fetch p.linkedSale sale
            where p.id = :pickupId
              and p.tenant.id = :tenantId
            """)
    Optional<Pickup> findByIdAndTenantIdWithDetails(@Param("pickupId") Long pickupId, @Param("tenantId") Long tenantId);

    @Query("""
            select p from Pickup p
            join fetch p.store s
            join fetch p.market m
            left join fetch p.linkedSale sale
            where p.tenant.id = :tenantId
              and (:marketId is null or m.id = :marketId)
              and lower(p.pickupNumber) = :pickupNumber
            order by p.createdAt desc, p.id desc
            """)
    List<Pickup> findAllByTenantIdAndMarketIdAndPickupNumber(
            @Param("tenantId") Long tenantId,
            @Param("marketId") Long marketId,
            @Param("pickupNumber") String pickupNumber);

    List<Pickup> findAllByLinkedSaleId(Long saleId);

    @Query("""
            select count(p) from Pickup p
            where p.tenant.id = :tenantId
              and (:marketIdsEmpty = true or p.market.id in :marketIds)
              and (:collaboratorUserId is null or p.collaboratorUserId = :collaboratorUserId)
              and p.status in :statuses
            """)
    long countDashboardPending(
            @Param("tenantId") Long tenantId,
            @Param("marketIds") List<Long> marketIds,
            @Param("marketIdsEmpty") boolean marketIdsEmpty,
            @Param("collaboratorUserId") Long collaboratorUserId,
            @Param("statuses") List<PickupStatus> statuses);
}
