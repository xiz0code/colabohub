package com.colaborapp.security;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.users.domain.RoleCode;

import lombok.RequiredArgsConstructor;

@Component("accessControl")
@RequiredArgsConstructor
public class AccessControlService {

    private static final Logger log = LoggerFactory.getLogger(AccessControlService.class);

    private final AuthenticatedUserService authenticatedUserService;
    private final StoreRepository storeRepository;

    public boolean canManageMarkets() {
        return hasRole(RoleCode.ADMIN_SYSTEM);
    }

    public boolean canManageUsers() {
        return hasRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
    }

    public boolean canManageCatalog() {
        return hasRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
    }

    public boolean canManageOwnCatalog() {
        return hasRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
    }

    public boolean canManageInventory() {
        return hasRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR);
    }

    public boolean canOperatePos() {
        return hasRole(RoleCode.ADMIN_MARKET, RoleCode.SELLER);
    }

    public boolean canReadInventory() {
        return hasRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR, RoleCode.STORE_USER, RoleCode.SELLER);
    }

    public boolean canReadInventoryDetails() {
        return hasRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR, RoleCode.STORE_USER);
    }

    public boolean canViewSalesDashboard() {
        return hasRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR);
    }

    public boolean canAccessOperationalReports() {
        return canViewSalesDashboard() || hasRole(RoleCode.STORE_USER);
    }

    public boolean canManageClosings(Long marketId) {
        if (hasRole(RoleCode.ADMIN_SYSTEM)) {
            return true;
        }
        return hasRole(RoleCode.ADMIN_MARKET) && canAccessMarket(marketId);
    }

    public boolean isSystemAdmin(ColaborAppUserPrincipal user) {
        return hasRole(user, RoleCode.ADMIN_SYSTEM);
    }

    public boolean hasRole(ColaborAppUserPrincipal user, RoleCode role) {
        return user != null && user.roles() != null && user.roles().contains(role.name());
    }

    public boolean hasRole(RoleCode role) {
        return hasRole(authenticatedUserService.requireCurrentPrincipal(), role);
    }

    public boolean hasRole(RoleCode... allowedRoles) {
        return hasAnyRole(authenticatedUserService.requireCurrentPrincipal(), allowedRoles);
    }

    public void requireAnyRole(RoleCode... roles) {
        ColaborAppUserPrincipal principal = authenticatedUserService.requireCurrentPrincipal();
        if (!hasAnyRole(principal, roles)) {
            deny("User %s lacks required role. Required=%s".formatted(principal.email(), List.of(roles)));
        }
    }

    @Transactional(readOnly = true)
    public boolean canAccessMarket(Long marketId) {
        return canAccessMarket(authenticatedUserService.requireCurrentPrincipal(), marketId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessMarket(ColaborAppUserPrincipal user, Long marketId) {
        if (user == null || marketId == null) {
            return false;
        }
        if (isSystemAdmin(user)) {
            return true;
        }
        if (hasRole(user, RoleCode.ADMIN_MARKET) || hasRole(user, RoleCode.COLLABORATOR) || hasRole(user, RoleCode.SELLER)) {
            return user.marketIds() != null && user.marketIds().contains(marketId);
        }
        if (hasRole(user, RoleCode.STORE_USER)) {
            return storeRepository.findAllById(user.storeIds() == null ? List.of() : user.storeIds()).stream()
                    .anyMatch(store -> store.getMarket().getId().equals(marketId));
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean canAccessStore(Long storeId) {
        return canAccessStore(authenticatedUserService.requireCurrentPrincipal(), storeId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessStore(ColaborAppUserPrincipal user, Long storeId) {
        if (user == null || storeId == null) {
            return false;
        }
        if (isSystemAdmin(user)) {
            return true;
        }
        if (hasRole(user, RoleCode.STORE_USER)) {
            return user.storeIds() != null && user.storeIds().contains(storeId);
        }
        return storeRepository.findById(storeId)
                .map(store -> (user.marketIds() != null && user.marketIds().contains(store.getMarket().getId()))
                        || (user.storeIds() != null && user.storeIds().contains(storeId)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public void requireMarketAccess(Long marketId) {
        ColaborAppUserPrincipal principal = authenticatedUserService.requireCurrentPrincipal();
        if (!canAccessMarket(principal, marketId)) {
            deny("User %s cannot access market %s".formatted(principal.email(), marketId));
        }
    }

    @Transactional(readOnly = true)
    public void requireStoreAccess(Long storeId) {
        ColaborAppUserPrincipal principal = authenticatedUserService.requireCurrentPrincipal();
        if (!canAccessStore(principal, storeId)) {
            deny("User %s cannot access store %s".formatted(principal.email(), storeId));
        }
    }

    public AuthenticatedUserService.CurrentAuthenticatedUser getCurrentUser() {
        return authenticatedUserService.getCurrentUserSnapshot();
    }

    public List<Long> currentMarketIds() {
        return authenticatedUserService.getCurrentUserSnapshot().marketIds();
    }

    public List<Long> currentStoreIds() {
        return authenticatedUserService.getCurrentUserSnapshot().storeIds();
    }

    private boolean hasAnyRole(ColaborAppUserPrincipal principal, RoleCode... allowedRoles) {
        Set<String> currentRoles = Set.copyOf(principal.roles() == null ? List.of() : principal.roles());
        return Set.of(allowedRoles).stream().map(RoleCode::name).anyMatch(currentRoles::contains);
    }

    private void deny(String message) {
        log.warn("Access denied: {}", message);
        throw new AccessDeniedException("No tienes permiso para realizar esta accion.");
    }
}
