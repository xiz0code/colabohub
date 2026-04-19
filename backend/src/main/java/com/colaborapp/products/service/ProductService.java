package com.colaborapp.products.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.common.web.dto.PageResponse;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.inventory.service.InventoryService;
import com.colaborapp.inventory.web.dto.StockAdjustmentRequest;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.products.web.dto.BarcodeLabelRequest;
import com.colaborapp.products.web.dto.ProductAuditLogResponse;
import com.colaborapp.products.web.dto.ProductCreateRequest;
import com.colaborapp.products.web.dto.ProductListQuery;
import com.colaborapp.products.web.dto.ProductPromotionGroupRequest;
import com.colaborapp.products.web.dto.ProductPromotionGroupResponse;
import com.colaborapp.products.web.dto.ProductPromotionRequest;
import com.colaborapp.products.web.dto.ProductPromotionResponse;
import com.colaborapp.products.web.dto.ProductResponse;
import com.colaborapp.products.web.dto.ProductSellerView;
import com.colaborapp.products.web.dto.ProductUpdateRequest;
import com.colaborapp.promotions.domain.ProductPromotion;
import com.colaborapp.promotions.domain.ProductPromotionGroup;
import com.colaborapp.promotions.domain.PromotionType;
import com.colaborapp.promotions.repository.ProductPromotionGroupRepository;
import com.colaborapp.promotions.repository.ProductPromotionRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.stores.service.StoreService;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Santiago");

    private final ProductRepository productRepository;
    private final StoreService storeService;
    private final StoreRepository storeRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final BarcodeGenerator barcodeGenerator;
    private final InventoryService inventoryService;
    private final AccessControlService accessControlService;
    private final UserRepository userRepository;
    private final ProductPromotionRepository productPromotionRepository;
    private final ProductPromotionGroupRepository productPromotionGroupRepository;
    private final ProductAuditService productAuditService;
    private final MarketRepository marketRepository;

    @Transactional(readOnly = true)
    public PageResponse<?> listProducts(ProductListQuery query) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR, RoleCode.STORE_USER, RoleCode.SELLER);
        PageRequest pageable = PageRequest.of(normalizePage(query.page()), normalizeSize(query.size()), Sort.by(Sort.Direction.ASC, "name"));
        if (query.storeId() != null) {
            accessControlService.requireStoreAccess(query.storeId());
        }

        String normalizedQuery = normalizeSearchPattern(query.query());
        if (normalizedQuery != null) {
            log.info("Product search param type: {}", normalizedQuery.getClass().getName());
        }

        Page<Product> page;
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            page = productRepository.search(tenantId, query.storeId(), query.ownerUserId(), query.status(), normalizedQuery, pageable);
        } else if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long ownerUserId = accessControlService.getCurrentUser().user().getId();
            if (ownerUserId == null) {
                return PageResponse.from(Page.empty(pageable));
            }
            page = productRepository.searchByOwnerUserId(tenantId, ownerUserId, query.storeId(), query.status(), normalizedQuery, pageable);
        } else {
            var marketIds = accessControlService.currentMarketIds();
            if (marketIds.isEmpty()) {
                return PageResponse.from(Page.empty(pageable));
            }
            page = productRepository.searchByMarketIds(tenantId, marketIds, query.storeId(), query.ownerUserId(), query.status(), normalizedQuery, pageable);
        }

        if (accessControlService.hasRole(RoleCode.SELLER)) {
            return PageResponse.from(page.map(this::toSellerView));
        }

        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public Object getProduct(Long productId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR, RoleCode.STORE_USER, RoleCode.SELLER);
        Product product = getProductEntity(productId, tenantId);
        requireProductReadAccess(product);
        if (accessControlService.hasRole(RoleCode.SELLER)) {
            return toSellerView(product);
        }
        return toResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        Tenant tenant = currentTenantProvider.getCurrentTenant();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
        Store store = resolveManagedStore(request.storeId(), tenant.getId());
        ensureStoreActive(store);
        validateUniqueSku(store.getId(), request.sku(), null);

        Product product = new Product();
        product.setTenant(tenant);
        User ownerUser = resolveOwnerUser(request.ownerUserId(), tenant.getId(), store);
        product.setStore(store);
        product.setOwnerUser(ownerUser);
        product.setPromotionGroup(resolvePromotionGroup(store, ownerUser, request.promotionGroupId(), request.promotionGroupName()));
        product.setName(request.name().trim());
        product.setSku(request.sku().trim());
        product.setDescription(trimToNull(request.description()));
        product.setSalePrice(request.salePrice());
        product.setCost(request.cost());
        product.setStock(request.initialStock());
        product.setStatus(ProductStatus.ACTIVE);
        String barcode = barcodeGenerator.generateUniqueBarcode();
        ensureUniqueBarcode(barcode);
        product.setBarcode(barcode);

        Product savedProduct = productRepository.save(product);
        syncPromotion(savedProduct, request.promotion());
        if (request.initialStock() > 0) {
            inventoryService.registerInitialStock(savedProduct, request.initialStock());
        }

        productAuditService.logChange(savedProduct, "Producto creado", null, savedProduct.getName());
        if (request.promotion() != null) {
            productAuditService.logChange(savedProduct, "Promocion", null, describePromotion(request.promotion()));
        }

        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductUpdateRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Product product = getProductEntity(productId, tenantId);
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        requireProductManagementAccess(product);
        validateUniqueSku(product.getStore().getId(), request.sku(), productId);

        String previousOwner = product.getOwnerUser() != null ? product.getOwnerUser().getFullName() : null;
        String previousName = product.getName();
        String previousSku = product.getSku();
        String previousDescription = product.getDescription();
        BigDecimal previousPrice = product.getSalePrice();
        Integer previousStock = product.getStock();
        String previousPromotionGroup = product.getPromotionGroup() != null ? product.getPromotionGroup().getName() : null;
        String previousPromotion = describePromotion(productPromotionRepository.findFirstByProductIdOrderByIdAsc(product.getId()).orElse(null));

        User ownerUser = resolveOwnerUser(request.ownerUserId(), tenantId, product.getStore());
        product.setOwnerUser(ownerUser);
        product.setPromotionGroup(resolvePromotionGroup(product.getStore(), ownerUser, request.promotionGroupId(), request.promotionGroupName()));
        product.setName(request.name().trim());
        product.setSku(request.sku().trim());
        product.setDescription(trimToNull(request.description()));
        product.setSalePrice(request.salePrice());
        product.setCost(request.cost());
        productRepository.save(product);

        if (!previousStock.equals(request.stock())) {
            inventoryService.adjustStock(new StockAdjustmentRequest(
                    product.getId(),
                    request.stock() - previousStock,
                    "PRODUCT_EDIT"));
        }

        syncPromotion(product, request.promotion());

        productAuditService.logChange(product, "Nombre del producto", previousName, product.getName());
        productAuditService.logChange(product, "Tienda responsable", previousOwner, product.getOwnerUser() != null ? product.getOwnerUser().getFullName() : null);
        productAuditService.logChange(product, "SKU", previousSku, product.getSku());
        productAuditService.logChange(product, "Descripcion", previousDescription, product.getDescription());
        productAuditService.logChange(product, "Precio", previousPrice, product.getSalePrice());
        productAuditService.logChange(product, "Stock", previousStock, request.stock());
        productAuditService.logChange(product, "Grupo promocional", previousPromotionGroup, product.getPromotionGroup() != null ? product.getPromotionGroup().getName() : null);
        productAuditService.logChange(product, "Promocion", previousPromotion, describePromotion(request.promotion()));

        return toResponse(productRepository.findByIdAndTenantId(productId, tenantId).orElse(product));
    }

    @Transactional(readOnly = true)
    public List<ProductPromotionGroupResponse> listPromotionGroups(Long storeId, Long ownerUserId) {
        Tenant tenant = currentTenantProvider.getCurrentTenant();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
        Store store = resolveManagedStore(storeId, tenant.getId());
        User ownerUser = resolveOwnerUser(ownerUserId, tenant.getId(), store);
        return productPromotionGroupRepository.findAllByTenantIdAndStoreIdAndOwnerUserIdOrderByNameAsc(tenant.getId(), store.getId(), ownerUser.getId())
                .stream()
                .map(this::toPromotionGroupResponse)
                .toList();
    }

    @Transactional
    public ProductPromotionGroupResponse createPromotionGroup(ProductPromotionGroupRequest request) {
        Tenant tenant = currentTenantProvider.getCurrentTenant();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
        Store store = resolveManagedStore(request.storeId(), tenant.getId());
        User ownerUser = resolveOwnerUser(request.ownerUserId(), tenant.getId(), store);
        ProductPromotionGroup group = findOrCreatePromotionGroup(store, ownerUser, request.name());
        return toPromotionGroupResponse(group);
    }

    @Transactional
    public void deletePromotionGroup(Long groupId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
        ProductPromotionGroup group = productPromotionGroupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos el grupo promocional."));
        requirePromotionGroupManagementAccess(group);
        if (productRepository.countByPromotionGroupId(group.getId()) > 0) {
            throw new BusinessException("No puedes eliminar un grupo promocional con productos asociados.");
        }
        productPromotionGroupRepository.delete(group);
    }

    @Transactional
    public ProductResponse updateStatus(Long productId, ProductStatus status) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Product product = getProductEntity(productId, tenantId);
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        accessControlService.requireStoreAccess(product.getStore().getId());
        ProductStatus previousStatus = product.getStatus();
        product.setStatus(status);
        productAuditService.logChange(product, "Estado", previousStatus, status);
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public Product getProductEntity(Long productId, Long tenantId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos el producto solicitado."));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsForBarcodeLabels(BarcodeLabelRequest request) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.STORE_USER);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        return request.items().stream()
                .map(item -> {
                    Product product = getProductEntity(item.productId(), tenantId);
                    requireProductReadAccess(product);
                    return toResponse(product);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductAuditLogResponse> getAuditTrail(Long productId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Product product = getProductEntity(productId, tenantId);
        requireProductReadAccess(product);
        return productAuditService.getAuditTrail(productId);
    }

    private Store resolveManagedStore(Long requestedStoreId, Long tenantId) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            List<Long> marketIds = accessControlService.currentMarketIds();
            if (marketIds.isEmpty()) {
                throw new BusinessException("No encontramos un Espacio activo para crear productos.");
            }

            Long marketId = marketIds.getFirst();
            Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("No encontramos el Espacio activo para crear productos."));
            return storeRepository.findByMarketIdAndType(marketId, StoreType.STOCK)
                    .orElseGet(() -> createDefaultStockStore(market));
        }

        if (requestedStoreId != null) {
            accessControlService.requireStoreAccess(requestedStoreId);
            return storeService.getStoreEntity(requestedStoreId, tenantId);
        }

        List<Long> marketIds = accessControlService.currentMarketIds();
        if (marketIds.isEmpty()) {
            throw new BusinessException("No encontramos una tienda activa para crear productos.");
        }

        Long marketId = marketIds.getFirst();
        Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));
        return storeRepository.findByMarketIdAndType(marketId, StoreType.STOCK)
                .orElseGet(() -> createDefaultStockStore(market));
    }

    private void ensureStoreActive(Store store) {
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new BusinessException("Solo puedes crear productos en tiendas activas.");
        }
    }

    private Store createDefaultStockStore(Market market) {
        Store newStore = new Store();
        newStore.setTenant(market.getTenant());
        newStore.setMarket(market);
        newStore.setCode("STOCK-" + market.getId());
        newStore.setName("Stock principal");
        newStore.setType(StoreType.STOCK);
        newStore.setStatus(StoreStatus.ACTIVE);
        return storeRepository.save(newStore);
    }

    private void validateUniqueSku(Long storeId, String sku, Long productId) {
        String normalizedSku = sku.trim();
        boolean exists = productId == null
                ? productRepository.existsByStoreIdAndSkuIgnoreCase(storeId, normalizedSku)
                : productRepository.existsByStoreIdAndSkuIgnoreCaseAndIdNot(storeId, normalizedSku, productId);
        if (exists) {
            throw new BusinessException("Ya existe un producto con ese SKU en esta tienda.");
        }
    }

    private void ensureUniqueBarcode(String barcode) {
        if (productRepository.existsByBarcode(barcode)) {
            throw new BusinessException("No pudimos generar un codigo de barras unico. Intenta nuevamente.");
        }
    }

    private String normalizeSearchPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
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

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ProductResponse toResponse(Product product) {
        ProductPromotionResponse promotion = findActivePromotion(product.getId())
                .map(this::toPromotionResponse)
                .orElse(null);
        return new ProductResponse(
                product.getId(),
                product.getStore().getId(),
                product.getStore().getName(),
                product.getOwnerUser() != null ? product.getOwnerUser().getId() : null,
                product.getOwnerUser() != null ? product.getOwnerUser().getFullName() : null,
                product.getPromotionGroup() != null ? product.getPromotionGroup().getId() : null,
                product.getPromotionGroup() != null ? product.getPromotionGroup().getName() : null,
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getSalePrice(),
                product.getCost(),
                product.getStock(),
                product.getStatus(),
                product.getBarcode(),
                promotion != null,
                promotion,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private ProductPromotionGroupResponse toPromotionGroupResponse(ProductPromotionGroup group) {
        return new ProductPromotionGroupResponse(
                group.getId(),
                group.getStore().getId(),
                group.getOwnerUser().getId(),
                group.getOwnerUser().getFullName(),
                group.getName());
    }

    private ProductSellerView toSellerView(Product product) {
        boolean hasPromotion = findActivePromotion(product.getId()).isPresent();
        boolean available = product.getStatus() == ProductStatus.ACTIVE && product.getStock() != null && product.getStock() > 0;
        return new ProductSellerView(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getSalePrice(),
                product.getStock(),
                product.getOwnerUser() != null ? product.getOwnerUser().getFullName() : null,
                hasPromotion,
                available);
    }

    private void requireProductReadAccess(Product product) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long currentUserId = accessControlService.getCurrentUser().user().getId();
            Long ownerUserId = product.getOwnerUser() != null ? product.getOwnerUser().getId() : null;
            if (currentUserId != null && currentUserId.equals(ownerUserId)) {
                return;
            }
        }

        accessControlService.requireStoreAccess(product.getStore().getId());
    }

    private void requireProductManagementAccess(Product product) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long currentUserId = accessControlService.getCurrentUser().user().getId();
            Long ownerUserId = product.getOwnerUser() != null ? product.getOwnerUser().getId() : null;
            if (currentUserId != null && currentUserId.equals(ownerUserId)) {
                return;
            }
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to perform this action.");
        }

        accessControlService.requireStoreAccess(product.getStore().getId());
    }

    private ProductPromotionResponse toPromotionResponse(ProductPromotion promotion) {
        return new ProductPromotionResponse(
                promotion.getType(),
                promotion.getType() == PromotionType.QUANTITY_BLOCK ? promotion.getBlockQuantity() : null,
                promotion.getType() == PromotionType.QUANTITY_BLOCK ? promotion.getBlockPrice() : null,
                promotion.getType() == PromotionType.PERCENTAGE_DISCOUNT || promotion.getType() == PromotionType.PAYMENT_METHOD_DISCOUNT
                        ? promotion.getPercentageDiscount()
                        : null,
                promotion.isAppliesToCash(),
                promotion.isAppliesToDebit(),
                promotion.getEndsAt());
    }

    private java.util.Optional<ProductPromotion> findActivePromotion(Long productId) {
        java.util.Optional<ProductPromotion> activePromotion = productPromotionRepository.findFirstActiveByProductId(productId, java.time.Instant.now());
        return activePromotion == null ? java.util.Optional.empty() : activePromotion;
    }

    private User resolveOwnerUser(Long ownerUserId, Long tenantId, Store store) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            User currentUser = accessControlService.getCurrentUser().user();
            if (currentUser == null || currentUser.getId() == null) {
                throw new BusinessException("No encontramos una Tienda activa para asignar el producto.");
            }

            return userRepository.findWithAccessById(currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("No encontramos la Tienda activa para asignar el producto."));
        }

        if (ownerUserId == null) {
            return null;
        }

        User owner = userRepository.findWithAccessById(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos al colaborador seleccionado."));

        boolean isCollaborator = owner.getRoles().stream().anyMatch(role -> role.getCode() == RoleCode.STORE_USER);
        if (!isCollaborator) {
            throw new BusinessException("Solo puedes asignar productos a colaboradores.");
        }

        boolean sameStore = owner.getStores().stream().anyMatch(userStore -> userStore.getId().equals(store.getId()));
        boolean sameMarket = owner.getMarkets().stream().anyMatch(userMarket -> userMarket.getId().equals(store.getMarket().getId()));
        if (!sameStore && !sameMarket) {
            throw new BusinessException("El colaborador debe pertenecer a la misma tienda del producto.");
        }

        return owner;
    }

    private ProductPromotionGroup resolvePromotionGroup(Store store, User ownerUser, Long groupId, String groupName) {
        if (groupId != null) {
            ProductPromotionGroup group = productPromotionGroupRepository.findByIdAndTenantId(groupId, store.getTenant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("No encontramos el grupo promocional seleccionado."));
            if (!group.getStore().getId().equals(store.getId()) || !group.getOwnerUser().getId().equals(ownerUser.getId())) {
                throw new BusinessException("El grupo promocional debe pertenecer a la misma Tienda responsable del producto.");
            }
            return group;
        }

        if (groupName == null || groupName.isBlank()) {
            return null;
        }

        return findOrCreatePromotionGroup(store, ownerUser, groupName);
    }

    private ProductPromotionGroup findOrCreatePromotionGroup(Store store, User ownerUser, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            throw new BusinessException("Ingresa un nombre para el grupo promocional.");
        }
        if (ownerUser == null || ownerUser.getId() == null) {
            throw new BusinessException("Selecciona la Tienda responsable antes de asignar un grupo promocional.");
        }

        return productPromotionGroupRepository.findByTenantIdAndStoreIdAndOwnerUserIdAndNameIgnoreCase(
                store.getTenant().getId(),
                store.getId(),
                ownerUser.getId(),
                name)
                .orElseGet(() -> {
                    ProductPromotionGroup group = new ProductPromotionGroup();
                    group.setTenant(store.getTenant());
                    group.setStore(store);
                    group.setOwnerUser(ownerUser);
                    group.setName(name);
                    return productPromotionGroupRepository.save(group);
                });
    }

    private void requirePromotionGroupManagementAccess(ProductPromotionGroup group) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long currentUserId = accessControlService.getCurrentUser().user().getId();
            if (currentUserId != null && currentUserId.equals(group.getOwnerUser().getId())) {
                return;
            }
        }

        accessControlService.requireStoreAccess(group.getStore().getId());
    }

    private void syncPromotion(Product product, ProductPromotionRequest request) {
        ProductPromotion existingPromotion = productPromotionRepository.findFirstByProductIdOrderByIdAsc(product.getId()).orElse(null);
        if (request == null) {
            if (existingPromotion != null) {
                productPromotionRepository.delete(existingPromotion);
            }
            return;
        }

        validatePromotion(request);
        ProductPromotion promotion = existingPromotion == null ? new ProductPromotion() : existingPromotion;
        promotion.setTenant(product.getTenant());
        promotion.setStore(product.getStore());
        promotion.setProduct(product);
        promotion.setType(request.type());
        promotion.setActive(true);

        if (request.type() == PromotionType.QUANTITY_BLOCK) {
            promotion.setName(request.quantity() + " x " + request.promotionalPrice().stripTrailingZeros().toPlainString());
            promotion.setBlockQuantity(request.quantity());
            promotion.setBlockPrice(request.promotionalPrice());
            promotion.setPercentageDiscount(null);
            promotion.setAppliesToCash(false);
            promotion.setAppliesToDebit(false);
        } else if (request.type() == PromotionType.PERCENTAGE_DISCOUNT) {
            promotion.setName(request.percentageDiscount().stripTrailingZeros().toPlainString() + "% descuento en cualquier medio de pago");
            promotion.setBlockQuantity(1);
            promotion.setBlockPrice(BigDecimal.ZERO);
            promotion.setPercentageDiscount(request.percentageDiscount());
            promotion.setAppliesToCash(false);
            promotion.setAppliesToDebit(false);
        } else {
            promotion.setName(buildPaymentMethodPromotionName(request));
            promotion.setBlockQuantity(1);
            promotion.setBlockPrice(BigDecimal.ZERO);
            promotion.setPercentageDiscount(request.percentageDiscount());
            promotion.setAppliesToCash(Boolean.TRUE.equals(request.appliesToCash()));
            promotion.setAppliesToDebit(Boolean.TRUE.equals(request.appliesToDebit()));
        }
        promotion.setStartsAt(null);
        promotion.setEndsAt(resolvePromotionEndInstant(request.endsAt()));
        productPromotionRepository.save(promotion);
    }

    private void validatePromotion(ProductPromotionRequest request) {
        if (request.type() == PromotionType.QUANTITY_BLOCK) {
            if (request.quantity() == null || request.quantity() < 2 || request.promotionalPrice() == null) {
                throw new BusinessException("Completa la cantidad y el precio promocional para la promocion por cantidad.");
            }
            return;
        }

        if (request.percentageDiscount() == null || request.percentageDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Completa el porcentaje de descuento para la promocion.");
        }

        if (request.type() == PromotionType.PAYMENT_METHOD_DISCOUNT
                && !Boolean.TRUE.equals(request.appliesToCash())
                && !Boolean.TRUE.equals(request.appliesToDebit())) {
            throw new BusinessException("Selecciona al menos un medio de pago para la promocion.");
        }
    }

    private String describePromotion(ProductPromotion promotion) {
        return promotion == null ? null : promotion.getName();
    }

    private String describePromotion(ProductPromotionRequest promotion) {
        if (promotion == null) {
            return null;
        }
        if (promotion.type() == PromotionType.QUANTITY_BLOCK) {
            return promotion.quantity() + " x " + promotion.promotionalPrice();
        }
        if (promotion.type() == PromotionType.PAYMENT_METHOD_DISCOUNT) {
            return buildPaymentMethodPromotionName(promotion);
        }
        return promotion.percentageDiscount() + "% descuento en cualquier medio de pago";
    }

    private String buildPaymentMethodPromotionName(ProductPromotionRequest request) {
        List<String> paymentMethods = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(request.appliesToCash())) {
            paymentMethods.add("efectivo");
        }
        if (Boolean.TRUE.equals(request.appliesToDebit())) {
            paymentMethods.add("debito");
        }
        return request.percentageDiscount().stripTrailingZeros().toPlainString()
                + "% descuento con "
                + String.join(" o ", paymentMethods);
    }

    private java.time.Instant resolvePromotionEndInstant(LocalDate endDate) {
        if (endDate == null) {
            return null;
        }
        return endDate.atTime(LocalTime.MAX).atZone(BUSINESS_ZONE).toInstant();
    }
}
