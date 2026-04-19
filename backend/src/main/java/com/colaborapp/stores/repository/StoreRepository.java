package com.colaborapp.stores.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;

public interface StoreRepository extends JpaRepository<Store, Long> {

    @Query("""
            select s from Store s
            where s.tenant.id = :tenantId
              and (:status is null or s.status = :status)
              and (
                  :query is null
                  or lower(s.code) like :query
                  or lower(s.name) like :query
              )
            """)
    Page<Store> search(
            @Param("tenantId") Long tenantId,
            @Param("status") StoreStatus status,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            select s from Store s
            where s.tenant.id = :tenantId
              and s.market.id in :marketIds
              and (:status is null or s.status = :status)
              and (
                  :query is null
                  or lower(s.code) like :query
                  or lower(s.name) like :query
              )
            """)
    Page<Store> searchByMarketIds(
            @Param("tenantId") Long tenantId,
            @Param("marketIds") java.util.Collection<Long> marketIds,
            @Param("status") StoreStatus status,
            @Param("query") String query,
            Pageable pageable);

    java.util.List<Store> findByTenantIdOrderByNameAsc(Long tenantId);

    Page<Store> findByTenantId(Long tenantId, Pageable pageable);

    Optional<Store> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Store> findByMarketIdAndType(Long marketId, StoreType type);

    Optional<Store> findFirstByTenantIdAndMarketIdAndType(Long tenantId, Long marketId, StoreType type);

    Optional<Store> findFirstByTenantIdAndMarketIdOrderByIdAsc(Long tenantId, Long marketId);

    boolean existsByTenantIdAndCodeIgnoreCase(Long tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNot(Long tenantId, String code, Long id);
}
