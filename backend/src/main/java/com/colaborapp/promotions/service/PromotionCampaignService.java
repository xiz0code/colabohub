package com.colaborapp.promotions.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.promotions.domain.ProductPromotion;
import com.colaborapp.promotions.domain.PromotionCampaign;
import com.colaborapp.promotions.domain.PromotionType;
import com.colaborapp.promotions.repository.ProductPromotionRepository;
import com.colaborapp.promotions.repository.PromotionCampaignRepository;
import com.colaborapp.promotions.web.dto.PromotionCampaignRequest;
import com.colaborapp.promotions.web.dto.PromotionCampaignResponse;
import com.colaborapp.promotions.web.dto.PromotionProductResponse;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromotionCampaignService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Santiago");

    private final PromotionCampaignRepository promotionCampaignRepository;
    private final ProductPromotionRepository productPromotionRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final MarketRepository marketRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public List<PromotionCampaignResponse> listPromotions(Long ownerUserId) {
        Tenant tenant = currentTenantProvider.getCurrentTenant();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
        Long effectiveOwnerUserId = accessControlService.hasRole(RoleCode.STORE_USER)
                ? accessControlService.getCurrentUser().user().getId()
                : ownerUserId;
        List<PromotionCampaign> campaigns;
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            campaigns = promotionCampaignRepository.findVisibleForTenant(tenant.getId(), effectiveOwnerUserId);
        } else {
            List<Long> marketIds = new ArrayList<>(accessControlService.currentMarketIds());
            if (marketIds.isEmpty() && accessControlService.hasRole(RoleCode.STORE_USER)) {
                marketIds.addAll(storeRepository.findAllById(accessControlService.currentStoreIds()).stream()
                        .map(store -> store.getMarket().getId())
                        .distinct()
                        .toList());
            }
            campaigns = marketIds.isEmpty()
                    ? List.of()
                    : promotionCampaignRepository.findVisibleByMarketIds(tenant.getId(), effectiveOwnerUserId, marketIds);
        }
        return campaigns.stream()
                .map(campaign -> toResponse(campaign, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public PromotionCampaignResponse getPromotion(Long promotionId) {
        PromotionCampaign campaign = getCampaign(promotionId);
        requireCampaignAccess(campaign);
        return toResponse(campaign, true);
    }

    @Transactional
    public PromotionCampaignResponse createPromotion(PromotionCampaignRequest request) {
        Tenant tenant = currentTenantProvider.getCurrentTenant();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
        User ownerUser = resolveOwnerUser(request.ownerUserId());
        Store store = resolveStoreForOwner(ownerUser, tenant.getId());

        PromotionCampaign campaign = new PromotionCampaign();
        campaign.setTenant(tenant);
        campaign.setStore(store);
        campaign.setOwnerUser(ownerUser);
        applyRequest(campaign, request);
        return toResponse(promotionCampaignRepository.save(campaign), true);
    }

    @Transactional
    public PromotionCampaignResponse updatePromotion(Long promotionId, PromotionCampaignRequest request) {
        PromotionCampaign campaign = getCampaign(promotionId);
        requireCampaignAccess(campaign);
        applyRequest(campaign, request);
        PromotionCampaign saved = promotionCampaignRepository.save(campaign);
        productPromotionRepository.findAllByPromotionCampaignIdOrderByProductNameAsc(saved.getId())
                .forEach(productPromotion -> copyCampaignToProductPromotion(saved, productPromotion));
        return toResponse(saved, true);
    }

    @Transactional
    public void deletePromotion(Long promotionId) {
        PromotionCampaign campaign = getCampaign(promotionId);
        requireCampaignAccess(campaign);
        productPromotionRepository.deleteByPromotionCampaignId(campaign.getId());
        promotionCampaignRepository.delete(campaign);
    }

    @Transactional
    public PromotionCampaignResponse assignProducts(Long promotionId, List<Long> productIds) {
        PromotionCampaign campaign = getCampaign(promotionId);
        requireCampaignAccess(campaign);
        if (!campaign.isActive()) {
            throw new BusinessException("La promocion debe estar activa para asignar productos.");
        }

        List<Product> products = productRepository.findAllByTenantIdAndIdInForUpdate(
                campaign.getTenant().getId(),
                productIds.stream().distinct().toList());
        if (products.size() != productIds.stream().distinct().count()) {
            throw new ResourceNotFoundException("Uno o mas productos no existen.");
        }

        for (Product product : products) {
            requireProductBelongsToCampaignOwner(campaign, product);
            productPromotionRepository.deleteByProductId(product.getId());
            ProductPromotion productPromotion = new ProductPromotion();
            productPromotion.setTenant(campaign.getTenant());
            productPromotion.setStore(product.getStore());
            productPromotion.setProduct(product);
            productPromotion.setPromotionCampaign(campaign);
            copyCampaignToProductPromotion(campaign, productPromotion);
            productPromotionRepository.save(productPromotion);
        }

        return toResponse(campaign, true);
    }

    private void applyRequest(PromotionCampaign campaign, PromotionCampaignRequest request) {
        validateRequest(request);
        campaign.setName(request.name().trim());
        campaign.setType(request.type());
        campaign.setActive(request.active() == null || request.active());
        campaign.setStartsAt(resolveStartInstant(request.startsAt()));
        campaign.setEndsAt(resolveEndInstant(request.endsAt()));

        if (request.type() == PromotionType.QUANTITY_BLOCK) {
            campaign.setBlockQuantity(request.quantity());
            campaign.setBlockPrice(request.promotionalPrice());
            campaign.setPercentageDiscount(null);
            campaign.setMinimumPurchaseAmount(null);
            clearPaymentMethods(campaign);
        } else if (request.type() == PromotionType.HIGHEST_PRICE_BUNDLE) {
            campaign.setBlockQuantity(request.quantity());
            campaign.setBlockPrice(BigDecimal.ZERO);
            campaign.setPercentageDiscount(null);
            campaign.setMinimumPurchaseAmount(null);
            clearPaymentMethods(campaign);
        } else if (request.type() == PromotionType.PERCENTAGE_DISCOUNT) {
            campaign.setBlockQuantity(1);
            campaign.setBlockPrice(BigDecimal.ZERO);
            campaign.setPercentageDiscount(request.percentageDiscount());
            campaign.setMinimumPurchaseAmount(null);
            clearPaymentMethods(campaign);
        } else if (request.type() == PromotionType.MIN_PURCHASE_AMOUNT_PERCENTAGE_DISCOUNT) {
            campaign.setBlockQuantity(1);
            campaign.setBlockPrice(BigDecimal.ZERO);
            campaign.setPercentageDiscount(request.percentageDiscount());
            campaign.setMinimumPurchaseAmount(request.minimumPurchaseAmount());
            clearPaymentMethods(campaign);
        } else {
            campaign.setBlockQuantity(1);
            campaign.setBlockPrice(BigDecimal.ZERO);
            campaign.setPercentageDiscount(request.percentageDiscount());
            campaign.setMinimumPurchaseAmount(null);
            campaign.setAppliesToCash(Boolean.TRUE.equals(request.appliesToCash()));
            campaign.setAppliesToDebit(Boolean.TRUE.equals(request.appliesToDebit()));
            campaign.setAppliesToCredit(Boolean.TRUE.equals(request.appliesToCredit()));
            campaign.setAppliesToTransfer(Boolean.TRUE.equals(request.appliesToTransfer()));
        }
    }

    private void validateRequest(PromotionCampaignRequest request) {
        if (request.type() == PromotionType.QUANTITY_BLOCK) {
            if (request.quantity() == null || request.quantity() < 2 || request.promotionalPrice() == null) {
                throw new BusinessException("Completa cantidad y precio promocional.");
            }
            return;
        }
        if (request.type() == PromotionType.HIGHEST_PRICE_BUNDLE) {
            if (request.quantity() == null || request.quantity() < 2) {
                throw new BusinessException("Completa cuantas unidades participan en la promocion paga el mayor.");
            }
            return;
        }
        if (request.percentageDiscount() == null || request.percentageDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Completa el porcentaje de descuento.");
        }
        if (request.type() == PromotionType.MIN_PURCHASE_AMOUNT_PERCENTAGE_DISCOUNT
                && (request.minimumPurchaseAmount() == null || request.minimumPurchaseAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("Completa el monto minimo de compra para la promocion.");
        }
        if (request.type() == PromotionType.PAYMENT_METHOD_DISCOUNT
                && !Boolean.TRUE.equals(request.appliesToCash())
                && !Boolean.TRUE.equals(request.appliesToDebit())
                && !Boolean.TRUE.equals(request.appliesToCredit())
                && !Boolean.TRUE.equals(request.appliesToTransfer())) {
            throw new BusinessException("Selecciona al menos un medio de pago.");
        }
    }

    private void copyCampaignToProductPromotion(PromotionCampaign campaign, ProductPromotion productPromotion) {
        productPromotion.setName(campaign.getName());
        productPromotion.setType(campaign.getType());
        productPromotion.setBlockQuantity(campaign.getBlockQuantity() == null ? 1 : campaign.getBlockQuantity());
        productPromotion.setBlockPrice(campaign.getBlockPrice() == null ? BigDecimal.ZERO : campaign.getBlockPrice());
        productPromotion.setPercentageDiscount(campaign.getPercentageDiscount());
        productPromotion.setMinimumPurchaseAmount(campaign.getMinimumPurchaseAmount());
        productPromotion.setAppliesToCash(campaign.isAppliesToCash());
        productPromotion.setAppliesToDebit(campaign.isAppliesToDebit());
        productPromotion.setAppliesToCredit(campaign.isAppliesToCredit());
        productPromotion.setAppliesToTransfer(campaign.isAppliesToTransfer());
        productPromotion.setActive(campaign.isActive());
        productPromotion.setStartsAt(campaign.getStartsAt());
        productPromotion.setEndsAt(campaign.getEndsAt());
    }

    private void requireProductBelongsToCampaignOwner(PromotionCampaign campaign, Product product) {
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException("Solo puedes asignar productos activos a promociones.");
        }
        if (!product.getStore().getId().equals(campaign.getStore().getId())) {
            throw new BusinessException("El producto debe pertenecer al mismo stock del Espacio.");
        }
        Long productOwnerId = product.getOwnerUser() != null ? product.getOwnerUser().getId() : null;
        if (!campaign.getOwnerUser().getId().equals(productOwnerId)) {
            throw new BusinessException("Los productos seleccionados deben pertenecer a la misma Tienda de la promocion.");
        }
    }

    private PromotionCampaign getCampaign(Long promotionId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        return promotionCampaignRepository.findByIdAndTenantIdWithDetails(promotionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos la promocion solicitada."));
    }

    private void requireCampaignAccess(PromotionCampaign campaign) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long currentUserId = accessControlService.getCurrentUser().user().getId();
            if (currentUserId != null && currentUserId.equals(campaign.getOwnerUser().getId())) {
                return;
            }
            throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para esta promocion.");
        }
        accessControlService.requireStoreAccess(campaign.getStore().getId());
    }

    private User resolveOwnerUser(Long ownerUserId) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            return accessControlService.getCurrentUser().user();
        }
        if (ownerUserId == null) {
            throw new BusinessException("Selecciona la Tienda para la promocion.");
        }
        User owner = userRepository.findWithAccessById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos la Tienda seleccionada."));
        boolean isStoreUser = owner.getRoles().stream().anyMatch(role -> role.getCode() == RoleCode.STORE_USER);
        if (!isStoreUser) {
            throw new BusinessException("La promocion debe asignarse a una Tienda.");
        }
        return owner;
    }

    private Store resolveStoreForOwner(User ownerUser, Long tenantId) {
        List<Long> marketIds = new ArrayList<>();
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            marketIds.addAll(ownerUser.getMarkets().stream().map(Market::getId).toList());
            marketIds.addAll(ownerUser.getStores().stream().map(store -> store.getMarket().getId()).toList());
        } else if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            marketIds.addAll(accessControlService.currentMarketIds());
            if (marketIds.isEmpty()) {
                marketIds.addAll(ownerUser.getStores().stream().map(store -> store.getMarket().getId()).toList());
            }
        } else {
            marketIds.addAll(accessControlService.currentMarketIds());
        }
        marketIds = marketIds.stream().distinct().toList();
        if (marketIds.isEmpty()) {
            throw new BusinessException("No encontramos un Espacio activo para esta Tienda.");
        }
        Long marketId = marketIds.getFirst();
        Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos el Espacio de la promocion."));
        return storeRepository.findByMarketIdAndType(marketId, StoreType.STOCK)
                .orElseGet(() -> createDefaultStockStore(market));
    }

    private Store createDefaultStockStore(Market market) {
        Store stockStore = new Store();
        stockStore.setTenant(market.getTenant());
        stockStore.setMarket(market);
        stockStore.setCode("STOCK-" + market.getId());
        stockStore.setName("Stock principal");
        stockStore.setType(StoreType.STOCK);
        stockStore.setStatus(StoreStatus.ACTIVE);
        return storeRepository.save(stockStore);
    }

    private void clearPaymentMethods(PromotionCampaign campaign) {
        campaign.setAppliesToCash(false);
        campaign.setAppliesToDebit(false);
        campaign.setAppliesToCredit(false);
        campaign.setAppliesToTransfer(false);
    }

    private Instant resolveStartInstant(LocalDate value) {
        return value == null ? null : value.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    private Instant resolveEndInstant(LocalDate value) {
        return value == null ? null : value.atTime(LocalTime.MAX).atZone(BUSINESS_ZONE).toInstant();
    }

    private PromotionCampaignResponse toResponse(PromotionCampaign campaign, boolean includeProducts) {
        List<PromotionProductResponse> products = includeProducts
                ? productPromotionRepository.findAllByPromotionCampaignIdOrderByProductNameAsc(campaign.getId()).stream()
                        .map(productPromotion -> {
                            Product product = productPromotion.getProduct();
                            return new PromotionProductResponse(product.getId(), product.getName(), product.getSku(), product.getStock());
                        })
                        .toList()
                : List.of();
        long productCount = includeProducts ? products.size() : productPromotionRepository.countByPromotionCampaignId(campaign.getId());
        return new PromotionCampaignResponse(
                campaign.getId(),
                campaign.getStore().getId(),
                campaign.getStore().getName(),
                campaign.getOwnerUser().getId(),
                campaign.getOwnerUser().getFullName(),
                campaign.getName(),
                campaign.getType(),
                campaign.getBlockQuantity(),
                campaign.getBlockPrice(),
                campaign.getPercentageDiscount(),
                campaign.getMinimumPurchaseAmount(),
                campaign.isAppliesToCash(),
                campaign.isAppliesToDebit(),
                campaign.isAppliesToCredit(),
                campaign.isAppliesToTransfer(),
                campaign.isActive(),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                productCount,
                products);
    }
}
