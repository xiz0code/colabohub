package com.colaborapp.markets.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.markets.web.dto.MarketRequest;
import com.colaborapp.markets.web.dto.MarketResponse;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.service.MarketAdminProvisioningService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;
    private final MarketAdminProvisioningService marketAdminProvisioningService;
    private final StoreRepository storeRepository;

    @Transactional
    public MarketResponse createMarket(MarketRequest request) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM);
        var tenant = currentTenantProvider.getCurrentTenant();
        validateUniqueName(tenant.getId(), request.name(), null);

        Market market = new Market();
        market.setTenant(tenant);
        applyRequest(market, request);
        Market savedMarket = marketRepository.save(market);
        ensureDefaultStockStore(savedMarket);
        marketAdminProvisioningService.provisionForMarket(savedMarket);
        return toResponse(savedMarket);
    }

    @Transactional(readOnly = true)
    public MarketResponse getMarket(Long marketId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Market market = getMarketEntity(marketId, tenantId);
        accessControlService.requireMarketAccess(market.getId());
        return toResponse(market);
    }

    @Transactional(readOnly = true)
    public List<MarketResponse> listMarkets() {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return marketRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                    .map(this::toResponse)
                    .toList();
        }

        List<Long> marketIds = accessControlService.currentMarketIds();
        if (marketIds.isEmpty()) {
            return List.of();
        }

        return marketRepository.findByTenantIdAndIdInOrderByNameAsc(tenantId, marketIds).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MarketResponse updateMarket(Long marketId, MarketRequest request) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Market market = getMarketEntity(marketId, tenantId);
        validateUniqueName(tenantId, request.name(), marketId);
        applyRequest(market, request);
        return toResponse(market);
    }

    @Transactional
    public MarketResponse updateStatus(Long marketId, boolean active) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Market market = getMarketEntity(marketId, tenantId);
        market.setActive(active);
        return toResponse(market);
    }

    @Transactional(readOnly = true)
    public Market getMarketEntity(Long marketId, Long tenantId) {
        return marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));
    }

    private void applyRequest(Market market, MarketRequest request) {
        market.setName(request.name().trim());
        market.setEmail(request.email().trim());
        market.setPhone(trimToNull(request.phone()));
        market.setContactName(trimToNull(request.contactName()));
        market.setDescription(trimToNull(request.description()));
        market.setCity(defaultString(request.city(), "Santiago"));
        market.setCurrency(defaultString(request.currency(), "CLP").toUpperCase());
        market.setUfEnabled(Boolean.TRUE.equals(request.ufEnabled()));
        market.setActive(request.active() == null || request.active());
    }

    private void validateUniqueName(Long tenantId, String name, Long currentMarketId) {
        String normalizedName = name.trim();
        boolean exists = currentMarketId == null
                ? marketRepository.existsByTenantIdAndNameIgnoreCase(tenantId, normalizedName)
                : marketRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, normalizedName, currentMarketId);
        if (exists) {
            throw new BusinessException("Ya existe una tienda con ese nombre.");
        }
    }

    private void ensureDefaultStockStore(Market market) {
        storeRepository.findByMarketIdAndType(market.getId(), StoreType.STOCK)
                .orElseGet(() -> storeRepository.save(buildStockStore(market)));
    }

    private Store buildStockStore(Market market) {
        Store stockStore = new Store();
        stockStore.setTenant(market.getTenant());
        stockStore.setMarket(market);
        stockStore.setCode("STOCK-" + market.getId());
        stockStore.setName("Stock principal");
        stockStore.setType(StoreType.STOCK);
        stockStore.setStatus(StoreStatus.ACTIVE);
        return stockStore;
    }

    private MarketResponse toResponse(Market market) {
        return new MarketResponse(
                market.getId(),
                market.getName(),
                market.getEmail(),
                market.getPhone(),
                market.getContactName(),
                market.getDescription(),
                market.getCity(),
                market.getCurrency(),
                market.isUfEnabled(),
                market.isActive(),
                market.getCreatedAt(),
                market.getUpdatedAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultString(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
