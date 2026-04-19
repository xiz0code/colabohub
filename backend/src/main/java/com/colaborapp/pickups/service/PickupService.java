package com.colaborapp.pickups.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.pickups.domain.Pickup;
import com.colaborapp.pickups.domain.PickupStatus;
import com.colaborapp.pickups.repository.PickupRepository;
import com.colaborapp.pickups.web.dto.PickupResponse;
import com.colaborapp.pickups.web.dto.PreparePickupCheckoutResponse;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.repository.SaleRepository;
import com.colaborapp.sales.service.PosSaleService;
import com.colaborapp.sales.web.dto.PosSaleResponse;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.users.domain.RoleCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PickupService {

    private final PickupRepository pickupRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final PosSaleService posSaleService;
    private final com.colaborapp.security.AccessControlService accessControlService;
    private final com.colaborapp.security.AuthenticatedUserService authenticatedUserService;

    @Transactional(readOnly = true)
    public List<PickupResponse> list(String query, PickupStatus status, Long storeId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var currentUser = authenticatedUserService.getCurrentUserSnapshot();

        Long marketId = null;
        List<Long> scopedStoreIds = List.of();
        boolean storeIdsEmpty = true;

        if (currentUser.roles().contains(RoleCode.STORE_USER.name())) {
            scopedStoreIds = resolveAccessibleStoreIds(currentUser.user().getId(), currentUser.storeIds(), tenantId);
            storeIdsEmpty = scopedStoreIds.isEmpty();
            if (storeIdsEmpty) {
                scopedStoreIds = List.of(-1L);
            }
        } else if (currentUser.roles().contains(RoleCode.ADMIN_MARKET.name()) || currentUser.roles().contains(RoleCode.SELLER.name())) {
            marketId = currentUser.marketIds().size() == 1 ? currentUser.marketIds().getFirst() : null;
        } else if (currentUser.roles().contains(RoleCode.ADMIN_SYSTEM.name())) {
            marketId = null;
        } else {
            throw new BusinessException("No tienes permiso para revisar retiros.");
        }

        if (storeId != null) {
            accessControlService.requireStoreAccess(storeId);
            scopedStoreIds = List.of(storeId);
            storeIdsEmpty = false;
        }

        String normalizedQuery = normalizeQuery(query);
        return pickupRepository.search(tenantId, marketId, scopedStoreIds, storeIdsEmpty, status, normalizedQuery).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PickupResponse create(Long storeId, String pickupNumber, String customerName, String description, boolean payable, BigDecimal amountDue) {
        requireCreateAccess();
        if (payable && (amountDue == null || amountDue.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("Ingresa un monto valido cuando el retiro es por pagar.");
        }

        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Store store = resolveStoreForCreate(storeId, tenantId);

        Pickup pickup = new Pickup();
        pickup.setTenant(currentTenantProvider.getCurrentTenant());
        pickup.setMarket(store.getMarket());
        pickup.setStore(store);
        pickup.setPickupNumber(pickupNumber.trim());
        pickup.setCustomerName(customerName.trim());
        pickup.setDescription(description.trim());
        pickup.setPayable(payable);
        pickup.setAmountDue(payable ? amountDue : null);
        pickup.setStatus(PickupStatus.PENDING);
        pickupRepository.save(pickup);
        return toResponse(pickup);
    }

    private Store resolveStoreForCreate(Long storeId, Long tenantId) {
        var currentUser = authenticatedUserService.getCurrentUserSnapshot();

        if (currentUser.roles().contains(RoleCode.STORE_USER.name())) {
            Long effectiveStoreId = storeId;
            if (effectiveStoreId == null) {
                List<Long> accessibleStoreIds = resolveAccessibleStoreIds(currentUser.user().getId(), currentUser.storeIds(), tenantId);
                effectiveStoreId = accessibleStoreIds.size() == 1 ? accessibleStoreIds.getFirst() : null;
            }

            if (effectiveStoreId == null) {
                throw new BusinessException("No pudimos identificar la Tienda asociada a tu cuenta para crear el retiro.");
            }

            Store store = storeRepository.findByIdAndTenantId(effectiveStoreId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("No encontramos la Tienda asociada a tu cuenta para el retiro."));
            requireStoreAccessForPickup(store.getId(), currentUser.user().getId(), currentUser.storeIds(), tenantId);
            return store;
        }

        if (storeId == null) {
            throw new BusinessException("Selecciona la Tienda responsable del retiro.");
        }

        Store store = storeRepository.findByIdAndTenantId(storeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos la Tienda seleccionada para el retiro."));
        accessControlService.requireStoreAccess(store.getId());
        return store;
    }

    @Transactional
    public PickupResponse markCollected(Long pickupId) {
        requireOperationalAccess();
        Pickup pickup = getPickup(pickupId);
        if (pickup.isPayable()) {
            throw new BusinessException("Los retiros por pagar deben cobrarse desde el POS antes de marcarse como retirados.");
        }
        if (pickup.getStatus() == PickupStatus.COLLECTED) {
            return toResponse(pickup);
        }
        if (pickup.getStatus() == PickupStatus.CANCELLED) {
            throw new BusinessException("Un retiro cancelado no puede marcarse como retirado.");
        }

        pickup.setStatus(PickupStatus.COLLECTED);
        pickup.setCollectedAt(Instant.now());
        pickup.setCollectedBy(authenticatedUserService.getCurrentUserSnapshot().user().getEmail());
        return toResponse(pickup);
    }

    @Transactional
    public PreparePickupCheckoutResponse prepareCheckout(Long pickupId) {
        requireOperationalAccess();
        Pickup pickup = getPickup(pickupId);
        if (!pickup.isPayable()) {
            throw new BusinessException("Este retiro ya figura como pagado y no necesita cobro por POS.");
        }
        if (pickup.getStatus() == PickupStatus.COLLECTED) {
            throw new BusinessException("Este retiro ya fue entregado.");
        }
        if (pickup.getStatus() == PickupStatus.CANCELLED) {
            throw new BusinessException("No puedes cobrar un retiro cancelado.");
        }

        if (pickup.getLinkedSale() != null) {
            Sale linkedSale = saleRepository.findByIdAndTenantId(pickup.getLinkedSale().getId(), currentTenantProvider.getCurrentTenant().getId())
                    .orElse(null);
            if (linkedSale != null && linkedSale.getStatus() == SaleStatus.OPEN) {
                pickup.setStatus(PickupStatus.CHECKOUT_IN_PROGRESS);
                return new PreparePickupCheckoutResponse(toResponse(pickup), posSaleService.getSale(linkedSale.getId()));
            }
            if (linkedSale != null && linkedSale.getStatus() == SaleStatus.CONFIRMED) {
                pickup.setStatus(PickupStatus.COLLECTED);
                pickup.setCollectedAt(linkedSale.getConfirmedAt());
                pickup.setCollectedBy(linkedSale.getUpdatedBy());
                return new PreparePickupCheckoutResponse(toResponse(pickup), posSaleService.getSale(linkedSale.getId()));
            }
            pickup.setLinkedSale(null);
            pickup.setStatus(PickupStatus.PENDING);
        }

        PosSaleResponse openSale = posSaleService.getOpenSaleOrNull(pickup.getMarket().getId());
        PosSaleResponse preparedSale = posSaleService.addManualItem(
                openSale.id(),
                new com.colaborapp.sales.web.dto.PosManualSaleItemRequest(
                        pickup.getStore().getId(),
                        "Retiro " + pickup.getPickupNumber(),
                        pickup.getDescription(),
                        pickup.getAmountDue(),
                        "pickup:" + pickup.getId(),
                        1));
        pickup.setLinkedSale(saleRepository.findByIdAndTenantId(preparedSale.id(), currentTenantProvider.getCurrentTenant().getId()).orElse(null));
        pickup.setStatus(PickupStatus.CHECKOUT_IN_PROGRESS);
        return new PreparePickupCheckoutResponse(toResponse(pickup), preparedSale);
    }

    @Transactional
    public PickupResponse cancel(Long pickupId) {
        requireOperationalAccess();
        Pickup pickup = getPickup(pickupId);

        if (pickup.getStatus() == PickupStatus.CANCELLED) {
            return toResponse(pickup);
        }
        if (pickup.getStatus() == PickupStatus.COLLECTED) {
            throw new BusinessException("No puedes anular un retiro que ya fue entregado.");
        }

        if (pickup.getLinkedSale() != null) {
            Sale linkedSale = saleRepository.findByIdAndTenantId(pickup.getLinkedSale().getId(), currentTenantProvider.getCurrentTenant().getId())
                    .orElse(null);
            if (linkedSale != null && linkedSale.getStatus() == SaleStatus.OPEN) {
                posSaleService.removeManualItemByReference(linkedSale.getId(), "pickup:" + pickup.getId());
            }
            pickup.setLinkedSale(null);
        }

        pickup.setStatus(PickupStatus.CANCELLED);
        return toResponse(pickup);
    }

    @Transactional
    public void markCollectedForSale(Long saleId) {
        for (Pickup pickup : pickupRepository.findAllByLinkedSaleId(saleId)) {
            if (pickup.getStatus() == PickupStatus.COLLECTED) {
                continue;
            }
            pickup.setStatus(PickupStatus.COLLECTED);
            pickup.setCollectedAt(Instant.now());
            pickup.setCollectedBy(pickup.getUpdatedBy());
        }
    }

    @Transactional
    public void resetCheckoutForSale(Long saleId) {
        for (Pickup pickup : pickupRepository.findAllByLinkedSaleId(saleId)) {
            if (pickup.getStatus() == PickupStatus.COLLECTED) {
                continue;
            }
            pickup.setLinkedSale(null);
            pickup.setStatus(PickupStatus.PENDING);
        }
    }

    private Pickup getPickup(Long pickupId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Pickup pickup = pickupRepository.findByIdAndTenantIdWithDetails(pickupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos el retiro solicitado."));
        var currentUser = authenticatedUserService.getCurrentUserSnapshot();
        requireStoreAccessForPickup(pickup.getStore().getId(), currentUser.user().getId(), currentUser.storeIds(), tenantId);
        return pickup;
    }

    private List<Long> resolveAccessibleStoreIds(Long userId, List<Long> assignedStoreIds, Long tenantId) {
        if (assignedStoreIds != null && !assignedStoreIds.isEmpty()) {
            return assignedStoreIds;
        }
        if (userId == null) {
            return List.of();
        }
        return productRepository.findDistinctStoresByTenantIdAndOwnerUserId(tenantId, userId).stream()
                .map(Store::getId)
                .toList();
    }

    private void requireStoreAccessForPickup(Long storeId, Long userId, List<Long> assignedStoreIds, Long tenantId) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            List<Long> accessibleStoreIds = resolveAccessibleStoreIds(userId, assignedStoreIds, tenantId);
            if (!accessibleStoreIds.contains(storeId)) {
                throw new BusinessException("No tienes acceso a la Tienda asociada a este retiro.");
            }
            return;
        }
        accessControlService.requireStoreAccess(storeId);
    }

    private void requireCreateAccess() {
        accessControlService.requireAnyRole(RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
    }

    private void requireOperationalAccess() {
        accessControlService.requireAnyRole(RoleCode.ADMIN_MARKET, RoleCode.SELLER);
    }

    private PickupResponse toResponse(Pickup pickup) {
        return new PickupResponse(
                pickup.getId(),
                pickup.getMarket().getId(),
                pickup.getMarket().getName(),
                pickup.getStore().getId(),
                pickup.getStore().getName(),
                pickup.getPickupNumber(),
                pickup.getCustomerName(),
                pickup.getDescription(),
                pickup.isPayable(),
                pickup.getAmountDue(),
                pickup.getStatus(),
                pickup.getLinkedSale() != null ? pickup.getLinkedSale().getId() : null,
                pickup.getCollectedAt(),
                pickup.getCollectedBy(),
                pickup.getCreatedAt());
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim().toLowerCase() + "%";
    }
}
