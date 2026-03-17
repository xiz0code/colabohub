package com.colaborapp.stores.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.common.web.dto.PageResponse;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.service.MarketService;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.stores.web.dto.StoreListQuery;
import com.colaborapp.stores.web.dto.StoreRequest;
import com.colaborapp.stores.web.dto.StoreResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final MarketService marketService;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> listStores(StoreListQuery query) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        PageRequest pageable = PageRequest.of(
                normalizePage(query.page()),
                normalizeSize(query.size()),
                Sort.by(Sort.Direction.ASC, "name"));

        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return PageResponse.from(storeRepository.search(
                            tenantId,
                            query.status(),
                            normalizeQuery(query.query()),
                            pageable)
                    .map(this::toResponse));
        }

        var marketIds = accessControlService.currentMarketIds();
        if (marketIds.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        return PageResponse.from(storeRepository.searchByMarketIds(
                        tenantId,
                        marketIds,
                        query.status(),
                        normalizeQuery(query.query()),
                        pageable)
                .map(this::toResponse));
    }

    @Transactional
    public StoreResponse createStore(StoreRequest request) {
        var tenant = currentTenantProvider.getCurrentTenant();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        accessControlService.requireMarketAccess(request.marketId());
        Market market = marketService.getMarketEntity(request.marketId(), tenant.getId());
        validateUniqueCode(tenant.getId(), request.code(), null);

        Store store = new Store();
        store.setTenant(tenant);
        store.setMarket(market);
        store.setCode(request.code().trim());
        store.setName(request.name().trim());
        store.setType(request.type());
        store.setStatus(StoreStatus.ACTIVE);

        return toResponse(storeRepository.save(store));
    }

    @Transactional
    public StoreResponse updateStore(Long storeId, StoreRequest request) {
        var tenant = currentTenantProvider.getCurrentTenant();
        Store store = getStoreEntity(storeId, tenant.getId());
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        accessControlService.requireStoreAccess(store.getId());
        accessControlService.requireMarketAccess(request.marketId());
        Market market = marketService.getMarketEntity(request.marketId(), tenant.getId());

        validateUniqueCode(tenant.getId(), request.code(), storeId);
        store.setMarket(market);
        store.setCode(request.code().trim());
        store.setName(request.name().trim());
        store.setType(request.type());

        return toResponse(store);
    }

    @Transactional
    public StoreResponse updateStatus(Long storeId, StoreStatus status) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Store store = getStoreEntity(storeId, tenantId);
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        accessControlService.requireStoreAccess(store.getId());
        store.setStatus(status);
        return toResponse(store);
    }

    @Transactional(readOnly = true)
    public Store getStoreEntity(Long storeId, Long tenantId) {
        return storeRepository.findByIdAndTenantId(storeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));
    }

    private void validateUniqueCode(Long tenantId, String code, Long currentStoreId) {
        String normalizedCode = code.trim();
        boolean exists = currentStoreId == null
                ? storeRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)
                : storeRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNot(tenantId, normalizedCode, currentStoreId);

        if (exists) {
            throw new BusinessException("Store code already exists for the current tenant.");
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private StoreResponse toResponse(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getMarket().getId(),
                store.getMarket().getName(),
                store.getCode(),
                store.getName(),
                store.getType(),
                store.getStatus(),
                store.getCreatedAt(),
                store.getUpdatedAt());
    }
}
