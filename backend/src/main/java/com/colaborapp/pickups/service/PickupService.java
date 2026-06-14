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
import com.colaborapp.sales.repository.SaleItemRepository;
import com.colaborapp.sales.repository.SaleRepository;
import com.colaborapp.sales.service.PosSaleService;
import com.colaborapp.sales.web.dto.PosSaleResponse;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PickupService {

    private static final String PICKUP_BARCODE_PREFIX = "RET-";
    private static final int PICKUP_BARCODE_PAD = 7;

    private final PickupRepository pickupRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final PosSaleService posSaleService;
    private final com.colaborapp.security.AccessControlService accessControlService;
    private final com.colaborapp.security.AuthenticatedUserService authenticatedUserService;

    @Transactional(readOnly = true)
    public List<PickupResponse> list(String query, PickupStatus status, Long storeId, Long selectedCollaboratorUserId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var currentUser = authenticatedUserService.getCurrentUserSnapshot();

        Long marketId = null;
        Long collaboratorUserId = null;
        List<Long> scopedStoreIds = List.of();
        boolean storeIdsEmpty = true;

        if (currentUser.roles().contains(RoleCode.STORE_USER.name())) {
            collaboratorUserId = currentUser.user().getId();
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
        if (selectedCollaboratorUserId != null) {
            if (collaboratorUserId != null && !collaboratorUserId.equals(selectedCollaboratorUserId)) {
                throw new BusinessException("No tienes acceso a los retiros de esta Tienda.");
            }
            collaboratorUserId = selectedCollaboratorUserId;
        }

        String normalizedQuery = normalizeQuery(query);
        return pickupRepository.search(tenantId, marketId, scopedStoreIds, storeIdsEmpty, collaboratorUserId, status, normalizedQuery).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PickupResponse create(Long storeId, Long collaboratorUserId, String pickupNumber, String customerName, String description, boolean payable, BigDecimal amountDue) {
        requireCreateAccess();
        if (payable && (amountDue == null || amountDue.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("Ingresa un monto valido cuando el retiro es por pagar.");
        }

        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var currentUser = authenticatedUserService.getCurrentUserSnapshot();
        User collaborator = resolveCollaboratorForCreate(collaboratorUserId, tenantId);
        Store store = resolveStoreForCreate(storeId, collaborator, tenantId);

        Pickup pickup = new Pickup();
        pickup.setTenant(currentTenantProvider.getCurrentTenant());
        pickup.setMarket(store.getMarket());
        pickup.setStore(store);
        if (collaborator != null) {
            pickup.setCollaboratorUserId(collaborator.getId());
            pickup.setCollaboratorNameSnapshot(collaborator.getFullName());
        } else if (currentUser.roles().contains(RoleCode.STORE_USER.name())) {
            pickup.setCollaboratorUserId(currentUser.user().getId());
            pickup.setCollaboratorNameSnapshot(currentUser.user().getFullName());
        }
        pickup.setPickupNumber(pickupNumber.trim());
        pickup.setCustomerName(customerName.trim());
        pickup.setDescription(description.trim());
        pickup.setPayable(payable);
        pickup.setAmountDue(payable ? amountDue : null);
        pickup.setStatus(PickupStatus.PENDING);
        pickupRepository.save(pickup);
        return toResponse(pickup);
    }

    private User resolveCollaboratorForCreate(Long collaboratorUserId, Long tenantId) {
        var currentUser = authenticatedUserService.getCurrentUserSnapshot();

        if (currentUser.roles().contains(RoleCode.STORE_USER.name())) {
            return userRepository.findWithAccessById(currentUser.user().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("No encontramos la Tienda activa para crear el retiro."));
        }

        if (collaboratorUserId == null) {
            return null;
        }

        User collaborator = userRepository.findWithAccessById(collaboratorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos la Tienda seleccionada para el retiro."));
        boolean isStoreUser = collaborator.getRoles().stream().anyMatch(role -> role.getCode() == RoleCode.STORE_USER);
        if (!collaborator.isActive() || !isStoreUser) {
            throw new BusinessException("Selecciona una Tienda activa asociada al Espacio.");
        }
        if (accessControlService.hasRole(RoleCode.ADMIN_MARKET)) {
            boolean canManageMarket = collaborator.getMarkets().stream().anyMatch(market -> accessControlService.currentMarketIds().contains(market.getId()))
                    || collaborator.getStores().stream().anyMatch(store -> accessControlService.currentMarketIds().contains(store.getMarket().getId()));
            if (!canManageMarket) {
                throw new BusinessException("No tienes acceso a la Tienda seleccionada.");
            }
        }
        return collaborator;
    }

    private Store resolveStoreForCreate(Long storeId, User collaborator, Long tenantId) {
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
            Pickup accessProbe = new Pickup();
            accessProbe.setStore(store);
            accessProbe.setCollaboratorUserId(currentUser.user().getId());
            requireStoreAccessForPickup(accessProbe, currentUser.user().getId(), currentUser.storeIds(), tenantId);
            return store;
        }

        if (collaborator != null) {
            Store store = resolveStockStoreForCollaborator(collaborator, tenantId);
            accessControlService.requireStoreAccess(store.getId());
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

    private Store resolveStockStoreForCollaborator(User collaborator, Long tenantId) {
        Long marketId = collaborator.getMarkets().stream()
                .map(market -> market.getId())
                .findFirst()
                .orElseGet(() -> collaborator.getStores().stream()
                        .map(store -> store.getMarket().getId())
                        .findFirst()
                        .orElse(null));
        if (marketId == null) {
            throw new BusinessException("La Tienda seleccionada no tiene un Espacio asociado.");
        }

        return storeRepository.findFirstByTenantIdAndMarketIdAndType(tenantId, marketId, StoreType.STOCK)
                .orElseGet(() -> {
                    Store stockStore = new Store();
                    stockStore.setTenant(currentTenantProvider.getCurrentTenant());
                    stockStore.setMarket(collaborator.getMarkets().stream()
                            .filter(market -> market.getId().equals(marketId))
                            .findFirst()
                            .orElseGet(() -> collaborator.getStores().stream()
                                    .map(Store::getMarket)
                                    .filter(market -> market.getId().equals(marketId))
                                    .findFirst()
                                    .orElseThrow(() -> new BusinessException("La Tienda seleccionada no tiene un Espacio asociado."))));
                    stockStore.setCode("STOCK-" + marketId);
                    stockStore.setName("Stock principal");
                    stockStore.setType(StoreType.STOCK);
                    stockStore.setStatus(StoreStatus.ACTIVE);
                    return storeRepository.save(stockStore);
                });
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
                if (saleItemRepository.findBySaleIdAndManualReference(linkedSale.getId(), "pickup:" + pickup.getId()).isPresent()) {
                    pickup.setStatus(PickupStatus.COLLECTED);
                    pickup.setCollectedAt(linkedSale.getConfirmedAt());
                    pickup.setCollectedBy(linkedSale.getUpdatedBy());
                    return new PreparePickupCheckoutResponse(toResponse(pickup), posSaleService.getSale(linkedSale.getId()));
                }
            }
            pickup.setLinkedSale(null);
            pickup.setStatus(PickupStatus.PENDING);
            pickup.setCollectedAt(null);
            pickup.setCollectedBy(null);
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
                        1,
                        pickup.getCollaboratorUserId(),
                        pickup.getCollaboratorNameSnapshot()));
        pickup.setLinkedSale(saleRepository.findByIdAndTenantId(preparedSale.id(), currentTenantProvider.getCurrentTenant().getId()).orElse(null));
        pickup.setStatus(PickupStatus.CHECKOUT_IN_PROGRESS);
        return new PreparePickupCheckoutResponse(toResponse(pickup), preparedSale);
    }

    @Transactional
    public PreparePickupCheckoutResponse prepareCheckoutByCode(String code) {
        requireOperationalAccess();

        String normalizedCode = normalizeCode(code);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Long marketId = authenticatedUserService.getCurrentUserSnapshot().activeMarketId();

        Pickup pickup = resolvePickupByCode(tenantId, marketId, normalizedCode);
        return prepareCheckout(pickup.getId());
    }

    @Transactional(readOnly = true)
    public PickupResponse getPickupForLabel(Long pickupId) {
        requirePickupViewerAccess();
        return toResponse(getPickup(pickupId));
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
        requireStoreAccessForPickup(pickup, currentUser.user().getId(), currentUser.storeIds(), tenantId);
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

    private void requireStoreAccessForPickup(Pickup pickup, Long userId, List<Long> assignedStoreIds, Long tenantId) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            if (pickup.getCollaboratorUserId() != null) {
                if (!pickup.getCollaboratorUserId().equals(userId)) {
                    throw new BusinessException("No tienes acceso a la Tienda asociada a este retiro.");
                }
                return;
            }

            List<Long> accessibleStoreIds = resolveAccessibleStoreIds(userId, assignedStoreIds, tenantId);
            if (!accessibleStoreIds.contains(pickup.getStore().getId())) {
                throw new BusinessException("No tienes acceso a la Tienda asociada a este retiro.");
            }
            return;
        }
        accessControlService.requireStoreAccess(pickup.getStore().getId());
    }

    private void requireCreateAccess() {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
    }

    private void requireOperationalAccess() {
        accessControlService.requireAnyRole(RoleCode.ADMIN_MARKET, RoleCode.SELLER);
    }

    private void requirePickupViewerAccess() {
        accessControlService.requireAnyRole(RoleCode.ADMIN_MARKET, RoleCode.SELLER, RoleCode.STORE_USER);
    }

    private PickupResponse toResponse(Pickup pickup) {
        String displayStoreName = pickup.getCollaboratorNameSnapshot() != null && !pickup.getCollaboratorNameSnapshot().isBlank()
                ? pickup.getCollaboratorNameSnapshot()
                : pickup.getStore().getName();
        return new PickupResponse(
                pickup.getId(),
                pickup.getMarket().getId(),
                pickup.getMarket().getName(),
                pickup.getStore().getId(),
                displayStoreName,
                buildPickupBarcode(pickup.getId()),
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

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Escanea o ingresa un codigo de retiro valido.");
        }
        return code.trim().toUpperCase();
    }

    private Pickup resolvePickupByCode(Long tenantId, Long marketId, String normalizedCode) {
        Long pickupId = parsePickupBarcode(normalizedCode);
        if (pickupId != null) {
            Pickup pickup = pickupRepository.findByIdAndTenantIdWithDetails(pickupId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("No encontramos un retiro por pagar con ese codigo."));
            if (marketId != null && !pickup.getMarket().getId().equals(marketId)) {
                throw new BusinessException("Ese retiro pertenece a otro Espacio.");
            }
            return pickup;
        }

        List<Pickup> matches = pickupRepository.findAllByTenantIdAndMarketIdAndPickupNumber(tenantId, marketId, normalizedCode.toLowerCase());
        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("No encontramos un retiro por pagar con ese codigo.");
        }
        if (matches.size() > 1) {
            throw new BusinessException("Encontramos mas de un retiro con ese numero. Usa el codigo de barras del retiro para evitar ambiguedades.");
        }
        return matches.getFirst();
    }

    private Long parsePickupBarcode(String normalizedCode) {
        if (!normalizedCode.startsWith(PICKUP_BARCODE_PREFIX)) {
            return null;
        }

        String numericSection = normalizedCode.substring(PICKUP_BARCODE_PREFIX.length()).trim();
        if (numericSection.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(numericSection);
        } catch (NumberFormatException exception) {
            throw new BusinessException("El codigo de retiro escaneado no es valido.");
        }
    }

    private String buildPickupBarcode(Long pickupId) {
        return PICKUP_BARCODE_PREFIX + String.format("%0" + PICKUP_BARCODE_PAD + "d", pickupId);
    }
}
