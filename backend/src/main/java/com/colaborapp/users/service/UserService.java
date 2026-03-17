package com.colaborapp.users.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.EmailNormalizer;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;
import com.colaborapp.users.web.dto.UserRequest;
import com.colaborapp.users.web.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MarketRepository marketRepository;
    private final StoreRepository storeRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final EmailNormalizer emailNormalizer;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        return userRepository.findAllByOrderByFullNameAsc().stream()
                .filter(this::canViewUser)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        userRepository.findByEmailIgnoreCase(request.email())
                .ifPresent(existing -> {
                    throw new BusinessException("A user with this email already exists.");
                });

        User user = new User();
        applyRequest(user, request);
        replaceAccess(user, resolveManagedMarketIds(request), resolveManagedStoreIds(request));
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UserRequest request) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        User user = userRepository.findWithAccessById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        ensureCanManageUser(user);

        userRepository.findByEmailIgnoreCase(request.email())
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessException("A user with this email already exists.");
                });

        applyRequest(user, request);
        replaceAccess(user, resolveManagedMarketIds(request), resolveManagedStoreIds(request));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateStatus(Long userId, boolean active) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        User user = userRepository.findWithAccessById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        ensureCanManageUser(user);
        user.setActive(active);
        return toResponse(user);
    }

    private void applyRequest(User user, UserRequest request) {
        validateManagedRole(request.role());
        user.setEmail(emailNormalizer.normalize(request.email()));
        user.setFullName(request.fullName().trim());
        user.setPhone(trimToNull(request.phone()));
        user.setContactName(trimToNull(request.contactName()));
        user.setDescription(trimToNull(request.description()));
        user.setActive(request.active() == null || request.active());
        user.setAuthProvider("GOOGLE");
        user.getRoles().clear();
        user.getRoles().add(resolveRole(request.role()));
    }

    private Role resolveRole(RoleCode roleCode) {
        return roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleCode));
    }

    private void replaceAccess(User user, List<Long> marketIds, List<Long> storeIds) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Set<Long> uniqueMarketIds = new LinkedHashSet<>(marketIds == null ? List.of() : marketIds);
        Set<Long> uniqueStoreIds = new LinkedHashSet<>(storeIds == null ? List.of() : storeIds);

        user.getMarkets().clear();
        user.getMarkets().addAll(uniqueMarketIds.stream()
                .map(marketId -> marketRepository.findByIdAndTenantId(marketId, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("La tienda seleccionada no existe o no está disponible.")))
                .toList());

        user.getStores().clear();
        user.getStores().addAll(uniqueStoreIds.stream()
                .map(storeId -> storeRepository.findByIdAndTenantId(storeId, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId)))
                .toList());

        for (Store store : user.getStores()) {
            if (user.getMarkets().stream().noneMatch(market -> market.getId().equals(store.getMarket().getId()))) {
                throw new BusinessException("Each collaborator store must belong to an allowed tienda.");
            }
        }
    }

    private UserResponse toResponse(User user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getCode().name())
                .sorted()
                .toList();
        List<Long> marketIds = user.getMarkets().stream()
                .map(Market::getId)
                .sorted()
                .toList();
        List<Long> storeIds = user.getStores().stream()
                .map(Store::getId)
                .sorted()
                .toList();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getContactName(),
                user.getDescription(),
                roles,
                marketIds,
                storeIds,
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private boolean canViewUser(User user) {
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return true;
        }

        if (!accessControlService.hasRole(RoleCode.ADMIN_MARKET)) {
            return false;
        }

        if (user.getRoles().stream().noneMatch(role -> role.getCode() == RoleCode.STORE_USER)) {
            return false;
        }

        Set<Long> allowedMarketIds = new LinkedHashSet<>(accessControlService.currentMarketIds());
        Set<Long> allowedStoreIds = new LinkedHashSet<>(accessControlService.currentStoreIds());

        return user.getMarkets().stream().anyMatch(market -> allowedMarketIds.contains(market.getId()))
                || user.getStores().stream().anyMatch(store -> allowedStoreIds.contains(store.getId())
                        || allowedMarketIds.contains(store.getMarket().getId()));
    }

    private void ensureCanManageUser(User user) {
        if (!canViewUser(user)) {
            throw new AccessDeniedException("You do not have permission to perform this action.");
        }
    }

    private void validateManagedRole(RoleCode roleCode) {
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return;
        }

        if (accessControlService.hasRole(RoleCode.ADMIN_MARKET) && roleCode == RoleCode.STORE_USER) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to perform this action.");
    }

    private List<Long> resolveManagedMarketIds(UserRequest request) {
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return request.marketIds() == null ? List.of() : request.marketIds();
        }

        List<Long> requested = request.marketIds() == null || request.marketIds().isEmpty()
                ? accessControlService.currentMarketIds()
                : request.marketIds();

        if (!new LinkedHashSet<>(accessControlService.currentMarketIds()).containsAll(requested)) {
            throw new AccessDeniedException("You do not have permission to perform this action.");
        }

        return requested;
    }

    private List<Long> resolveManagedStoreIds(UserRequest request) {
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return request.storeIds() == null ? List.of() : request.storeIds();
        }

        List<Long> requested = request.storeIds() == null ? List.of() : request.storeIds();
        Set<Long> allowedStoreIds = new LinkedHashSet<>(accessControlService.currentStoreIds());
        Set<Long> allowedMarketIds = new LinkedHashSet<>(accessControlService.currentMarketIds());
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();

        for (Long storeId : requested) {
            Store store = storeRepository.findByIdAndTenantId(storeId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));
            if (!allowedStoreIds.contains(storeId) && !allowedMarketIds.contains(store.getMarket().getId())) {
                throw new AccessDeniedException("You do not have permission to perform this action.");
            }
        }

        return requested;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
