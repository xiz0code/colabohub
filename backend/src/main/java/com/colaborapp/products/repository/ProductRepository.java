package com.colaborapp.products.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long> {

    long countByTenantIdAndStatus(Long tenantId, ProductStatus status);

    long countByTenantIdAndStatusAndStockLessThanEqual(Long tenantId, ProductStatus status, Integer stock);

    long countByTenantIdAndStore_Market_IdInAndStatus(Long tenantId, java.util.Collection<Long> marketIds, ProductStatus status);

    long countByTenantIdAndStore_Market_IdInAndStatusAndStockLessThanEqual(
            Long tenantId,
            java.util.Collection<Long> marketIds,
            ProductStatus status,
            Integer stock);

    long countByTenantIdAndStore_IdInAndStatus(Long tenantId, java.util.Collection<Long> storeIds, ProductStatus status);

    long countByTenantIdAndStore_IdInAndStatusAndStockLessThanEqual(
            Long tenantId,
            java.util.Collection<Long> storeIds,
            ProductStatus status,
            Integer stock);

    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            select p from Product p
            join fetch p.store s
            where p.id = :id
              and p.tenant.id = :tenantId
            """)
    Optional<Product> findByIdAndTenantIdWithStore(@Param("id") Long id, @Param("tenantId") Long tenantId);

    boolean existsByBarcode(String barcode);

    boolean existsByStoreIdAndSkuIgnoreCase(Long storeId, String sku);

    boolean existsByStoreIdAndSkuIgnoreCaseAndIdNot(Long storeId, String sku, Long id);

    @Query("""
            select p from Product p
            join fetch p.store s
            left join fetch p.ownerUser owner
            where p.tenant.id = :tenantId
              and p.status = :status
              and p.barcode = :barcode
            """)
    Optional<Product> findByBarcodeForPos(
            @Param("tenantId") Long tenantId,
            @Param("barcode") String barcode,
            @Param("status") ProductStatus status);

    @Query("""
            select p from Product p
            join fetch p.store s
            left join fetch p.ownerUser owner
            where p.tenant.id = :tenantId
              and p.status = :status
              and lower(p.sku) = lower(:sku)
            """)
    Optional<Product> findBySkuForPos(
            @Param("tenantId") Long tenantId,
            @Param("sku") String sku,
            @Param("status") ProductStatus status);

    @Query("""
            select p from Product p
            join fetch p.store s
            left join fetch p.ownerUser owner
            where p.tenant.id = :tenantId
              and p.status = :status
              and lower(p.name) like lower(concat('%', :name, '%'))
            order by p.name asc
            """)
    Page<Product> findByNameForPos(
            @Param("tenantId") Long tenantId,
            @Param("name") String name,
            @Param("status") ProductStatus status,
            Pageable pageable);

    @Query("""
            select p from Product p
            join fetch p.store s
            left join fetch p.ownerUser owner
            where p.tenant.id = :tenantId
              and p.status = :status
              and (
                  lower(p.barcode) like lower(concat('%', :query, '%'))
                  or lower(p.sku) like lower(concat('%', :query, '%'))
                  or lower(p.name) like lower(concat('%', :query, '%'))
              )
            order by
              case
                when lower(p.barcode) = lower(:query) then 0
                when lower(p.sku) = lower(:query) then 1
                else 2
              end,
              p.name asc
            """)
    Page<Product> searchForPos(
            @Param("tenantId") Long tenantId,
            @Param("query") String query,
            @Param("status") ProductStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Product p
            join fetch p.store s
            where p.tenant.id = :tenantId
              and p.id in :productIds
            """)
    java.util.List<Product> findAllByTenantIdAndIdInForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("productIds") java.util.List<Long> productIds);

    @Query(
            value = """
            select p from Product p
            join fetch p.store s
            where p.tenant.id = :tenantId
              and (:storeId is null or s.id = :storeId)
              and (:status is null or p.status = :status)
              and (
                  :query is null
                  or lower(p.barcode) like lower(concat('%', :query, '%'))
                  or lower(p.sku) like lower(concat('%', :query, '%'))
                  or lower(p.name) like lower(concat('%', :query, '%'))
              )
            """,
            countQuery = """
            select count(p) from Product p
            where p.tenant.id = :tenantId
              and (:storeId is null or p.store.id = :storeId)
              and (:status is null or p.status = :status)
              and (
                  :query is null
                  or lower(p.barcode) like lower(concat('%', :query, '%'))
                  or lower(p.sku) like lower(concat('%', :query, '%'))
                  or lower(p.name) like lower(concat('%', :query, '%'))
              )
            """)
    Page<Product> search(
            @Param("tenantId") Long tenantId,
            @Param("storeId") Long storeId,
            @Param("status") ProductStatus status,
            @Param("query") String query,
            Pageable pageable);

    @Query(
            value = """
            select p from Product p
            join fetch p.store s
            where p.tenant.id = :tenantId
              and s.market.id in :marketIds
              and (:storeId is null or s.id = :storeId)
              and (:status is null or p.status = :status)
              and (
                  :query is null
                  or lower(p.barcode) like lower(concat('%', :query, '%'))
                  or lower(p.sku) like lower(concat('%', :query, '%'))
                  or lower(p.name) like lower(concat('%', :query, '%'))
              )
            """,
            countQuery = """
            select count(p) from Product p
            where p.tenant.id = :tenantId
              and p.store.market.id in :marketIds
              and (:storeId is null or p.store.id = :storeId)
              and (:status is null or p.status = :status)
              and (
                  :query is null
                  or lower(p.barcode) like lower(concat('%', :query, '%'))
                  or lower(p.sku) like lower(concat('%', :query, '%'))
                  or lower(p.name) like lower(concat('%', :query, '%'))
              )
            """)
    Page<Product> searchByMarketIds(
            @Param("tenantId") Long tenantId,
            @Param("marketIds") java.util.Collection<Long> marketIds,
            @Param("storeId") Long storeId,
            @Param("status") ProductStatus status,
            @Param("query") String query,
            Pageable pageable);

    @Query(
            value = """
            select p from Product p
            join fetch p.store s
            where p.tenant.id = :tenantId
              and s.id in :storeIds
              and (:storeId is null or s.id = :storeId)
              and (:status is null or p.status = :status)
              and (
                  :query is null
                  or lower(p.barcode) like lower(concat('%', :query, '%'))
                  or lower(p.sku) like lower(concat('%', :query, '%'))
                  or lower(p.name) like lower(concat('%', :query, '%'))
              )
            """,
            countQuery = """
            select count(p) from Product p
            where p.tenant.id = :tenantId
              and p.store.id in :storeIds
              and (:storeId is null or p.store.id = :storeId)
              and (:status is null or p.status = :status)
              and (
                  :query is null
                  or lower(p.barcode) like lower(concat('%', :query, '%'))
                  or lower(p.sku) like lower(concat('%', :query, '%'))
                  or lower(p.name) like lower(concat('%', :query, '%'))
              )
            """)
    Page<Product> searchByStoreIds(
            @Param("tenantId") Long tenantId,
            @Param("storeIds") java.util.Collection<Long> storeIds,
            @Param("storeId") Long storeId,
            @Param("status") ProductStatus status,
            @Param("query") String query,
            Pageable pageable);
}
