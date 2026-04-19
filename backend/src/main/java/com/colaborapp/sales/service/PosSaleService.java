package com.colaborapp.sales.service;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.inventory.domain.StockMovement;
import com.colaborapp.inventory.domain.StockMovementType;
import com.colaborapp.inventory.repository.StockMovementRepository;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.pickups.domain.PickupStatus;
import com.colaborapp.pickups.repository.PickupRepository;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.AuthenticatedUserService;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleItem;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.domain.SaleStoreSummary;
import com.colaborapp.sales.repository.SaleItemRepository;
import com.colaborapp.sales.repository.SaleRepository;
import com.colaborapp.sales.repository.SaleStoreSummaryRepository;
import com.colaborapp.sales.web.dto.CreatePosSaleRequest;
import com.colaborapp.sales.web.dto.PosSaleSummaryResponse;
import com.colaborapp.sales.web.dto.PosPaymentMethodUpdateRequest;
import com.colaborapp.sales.web.dto.PosManualSaleItemRequest;
import com.colaborapp.sales.web.dto.PosSaleItemRequest;
import com.colaborapp.sales.web.dto.PosSaleItemResponse;
import com.colaborapp.sales.web.dto.PosSaleItemScanRequest;
import com.colaborapp.sales.web.dto.PosSaleItemUpdateRequest;
import com.colaborapp.sales.web.dto.PosSaleResponse;
import com.colaborapp.sales.web.dto.PosSaleStoreSummaryResponse;
import com.colaborapp.settings.service.CommissionSettingsService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.users.domain.RoleCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PosSaleService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, HALF_UP);

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleStoreSummaryRepository saleStoreSummaryRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final StockMovementRepository stockMovementRepository;
    private final MarketRepository marketRepository;
    private final PickupRepository pickupRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final SaleNumberGenerator saleNumberGenerator;
    private final PosPricingService posPricingService;
    private final PosProductLookupService posProductLookupService;
    private final AccessControlService accessControlService;
    private final AuthenticatedUserService authenticatedUserService;
    private final CommissionSettingsService commissionSettingsService;

    @Transactional
    public PosSaleResponse createSale(CreatePosSaleRequest request) {
        requirePosWriteAccess();
        var tenant = currentTenantProvider.getCurrentTenant();
        Long marketId = resolveAuthenticatedPosMarketId();

        var existingOpenSale = resolveExistingOpenSale(tenant.getId(), marketId);
        if (existingOpenSale.isPresent()) {
            Sale sale = existingOpenSale.get();
            if (marketId != null && sale.getMarket() == null) {
                sale.setMarket(resolveMarket(tenant.getId(), marketId));
                saleRepository.save(sale);
            }
            requireSaleAccess(sale);
            return buildResponse(
                    sale,
                    saleItemRepository.findAllBySaleIdWithDetails(sale.getId()),
                    saleStoreSummaryRepository.findAllBySaleIdWithStore(sale.getId()));
        }

        Sale sale = new Sale();
        sale.setTenant(tenant);
        sale.setMarket(resolveMarket(tenant.getId(), marketId));
        sale.setSaleNumber(saleNumberGenerator.next());
        sale.setStatus(SaleStatus.OPEN);
        sale.setPaymentMethod(request != null && request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.CASH);
        sale.setSubtotalAmount(ZERO);
        sale.setTotalDiscountAmount(ZERO);
        sale.setTotalAmount(ZERO);
        sale.setTotalCommissionAmount(ZERO);
        sale.setTotalNetAmount(ZERO);
        sale.setUfValue(sale.getMarket() != null ? sale.getMarket().getUfValue() : null);
        sale.setCommissionUfValue(null);
        sale.setCommissionPercentageValue(null);
        sale.setOpenedAt(Instant.now());
        sale.setConfirmedAt(null);
        sale.setCancelledAt(null);
        sale.setCancelledBy(null);
        sale.setCancellationReason(null);
        saleRepository.save(sale);
        return buildResponse(sale, List.of(), List.of());
    }

    @Transactional
    public PosSaleResponse getOpenSaleOrNull(Long marketId) {
        return createSale(new CreatePosSaleRequest(PaymentMethod.CASH, marketId));
    }

    @Transactional(readOnly = true)
    public List<PosSaleSummaryResponse> listSales() {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Long marketId = resolveAuthenticatedPosMarketId();

        return saleRepository.findTop100ByTenantIdAndMarketIdOrderByOpenedAtDescIdDesc(tenantId, marketId).stream()
                .map(this::buildSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PosSaleResponse getSale(Long saleId) {
        Sale sale = getSaleEntity(saleId);
        return buildResponse(
                sale,
                saleItemRepository.findAllBySaleIdWithDetails(sale.getId()),
                saleStoreSummaryRepository.findAllBySaleIdWithStore(sale.getId()));
    }

    @Transactional
    public PosSaleResponse addItem(Long saleId, PosSaleItemRequest request) {
        requirePosWriteAccess();
        Sale sale = getOpenSale(saleId);
        Product product = getProductForSale(request.productId(), sale.getTenant().getId());

        SaleItem item = saleItemRepository.findBySaleIdAndProductId(saleId, product.getId())
                .orElseGet(() -> createSaleItem(sale, product));
        int requestedQuantity = item.getQuantity() + request.quantity();
        ensureProductStockAvailable(product, requestedQuantity);
        item.setQuantity(requestedQuantity);
        saleItemRepository.save(item);

        return recalculateSaleTotals(sale);
    }

    @Transactional
    public PosSaleResponse addManualItem(Long saleId, PosManualSaleItemRequest request) {
        requirePosWriteAccess();
        Sale sale = getOpenSale(saleId);
        Store store = resolveStoreForManualItem(request.storeId(), sale.getTenant().getId());

        SaleItem item = (request.reference() != null && !request.reference().isBlank())
                ? saleItemRepository.findBySaleIdAndManualReference(saleId, request.reference().trim()).orElseGet(() -> createManualSaleItem(sale, store, request))
                : createManualSaleItem(sale, store, request);

        item.setQuantity(request.quantity());
        item.setBaseUnitPrice(request.amount().setScale(2, HALF_UP));
        item.setProductNameSnapshot(request.itemName().trim());
        item.setAppliedPromotionName(normalizeOptionalText(request.description()));
        item.setManualEntry(true);
        item.setManualReference(normalizeOptionalText(request.reference()));
        saleItemRepository.save(item);

        return recalculateSaleTotals(sale);
    }

    @Transactional
    public PosSaleResponse scanItem(Long saleId, PosSaleItemScanRequest request) {
        requirePosWriteAccess();
        String normalizedQuery = request.query().trim();
        int quantity = request.quantity() == null ? 1 : request.quantity();
        if (quantity < 1) {
            throw new BusinessException("La cantidad escaneada debe ser al menos 1.");
        }

        Sale sale = getOpenSale(saleId);
        Product product = posProductLookupService.resolveForSale(normalizedQuery);
        return addItem(saleId, new PosSaleItemRequest(product.getId(), quantity));
    }

    @Transactional
    public PosSaleResponse updateItem(Long saleId, Long itemId, PosSaleItemUpdateRequest request) {
        requirePosWriteAccess();
        Sale sale = getOpenSale(saleId);
        SaleItem item = saleItemRepository.findByIdAndSaleId(itemId, saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale item not found: " + itemId));
        if (!item.isManualEntry() && item.getProduct() != null) {
            Product product = getProductForSale(item.getProduct().getId(), sale.getTenant().getId());
            ensureProductStockAvailable(product, request.quantity());
            item.setProduct(product);
            item.setStore(product.getStore());
        }
        item.setQuantity(request.quantity());
        saleItemRepository.save(item);
        return recalculateSaleTotals(sale);
    }

    @Transactional
    public PosSaleResponse removeItem(Long saleId, Long itemId) {
        requirePosWriteAccess();
        Sale sale = getOpenSale(saleId);
        SaleItem item = saleItemRepository.findByIdAndSaleId(itemId, saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale item not found: " + itemId));
        saleItemRepository.delete(item);
        return recalculateSaleTotals(sale);
    }

    @Transactional
    public PosSaleResponse removeManualItemByReference(Long saleId, String reference) {
        requirePosWriteAccess();
        Sale sale = getOpenSale(saleId);
        SaleItem item = saleItemRepository.findBySaleIdAndManualReference(saleId, reference)
                .orElseThrow(() -> new ResourceNotFoundException("Manual sale item not found for reference: " + reference));
        saleItemRepository.delete(item);
        return recalculateSaleTotals(sale);
    }

    @Transactional
    public PosSaleResponse updatePaymentMethod(Long saleId, PosPaymentMethodUpdateRequest request) {
        requirePosWriteAccess();
        Sale sale = getOpenSale(saleId);
        sale.setPaymentMethod(request.paymentMethod());
        return recalculateSaleTotals(sale);
    }

    @Transactional
    public PosSaleResponse recalculate(Long saleId) {
        requirePosWriteAccess();
        return recalculateSaleTotals(getOpenSale(saleId));
    }

    @Transactional
    public PosSaleResponse cancel(Long saleId, String reason) {
        requirePosWriteAccess();
        Sale sale = getSaleEntity(saleId);
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessException("Sale is already cancelled.");
        }
        if (sale.getStatus() != SaleStatus.CONFIRMED && sale.getStatus() != SaleStatus.OPEN) {
            throw new BusinessException("Only open or confirmed sales can be cancelled.");
        }

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new BusinessException("Ingresa un motivo para anular la venta.");
        }

        List<SaleItem> items = saleItemRepository.findAllBySaleIdWithDetails(saleId);
        Map<Long, Product> lockedProducts = new HashMap<>();
        for (Product product : productRepository.findAllByTenantIdAndIdInForUpdate(
                sale.getTenant().getId(),
                items.stream().map(SaleItem::getProduct).filter(java.util.Objects::nonNull).map(Product::getId).distinct().toList())) {
            lockedProducts.put(product.getId(), product);
        }

        if (sale.getStatus() == SaleStatus.CONFIRMED) {
            for (SaleItem item : items) {
                if (item.isManualEntry() || item.getProduct() == null) {
                    continue;
                }
                Product product = lockedProducts.get(item.getProduct().getId());
                if (product == null) {
                    throw new ResourceNotFoundException("Product not found for sale item: " + item.getProduct().getId());
                }

                int previousStock = product.getStock();
                int newStock = previousStock + item.getQuantity();
                product.setStock(newStock);

                StockMovement movement = new StockMovement();
                movement.setTenant(product.getTenant());
                movement.setProduct(product);
                movement.setStore(product.getStore());
                movement.setType(StockMovementType.ADJUSTMENT);
                movement.setQuantity(item.getQuantity());
                movement.setPreviousStock(previousStock);
                movement.setNewStock(newStock);
                movement.setReferenceType("SALE_CANCEL");
                movement.setReferenceId(sale.getId());
                stockMovementRepository.save(movement);
            }
        }

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledAt(Instant.now());
        sale.setCancelledBy(authenticatedUserService.getCurrentUserSnapshot().user().getEmail());
        sale.setCancellationReason(normalizedReason);
        pickupRepository.findAllByLinkedSaleId(saleId).forEach(pickup -> {
            if (pickup.getStatus() != PickupStatus.COLLECTED) {
                pickup.setLinkedSale(null);
                pickup.setStatus(PickupStatus.PENDING);
            }
        });
        return buildResponse(
                sale,
                saleItemRepository.findAllBySaleIdWithDetails(saleId),
                saleStoreSummaryRepository.findAllBySaleIdWithStore(saleId));
    }

    @Transactional
    public PosSaleResponse confirm(Long saleId) {
        requirePosWriteAccess();
        return confirmSale(saleId);
    }

    @Transactional
    public PosSaleResponse confirmSale(Long saleId) {
        Sale sale = getSaleEntity(saleId);
        if (sale.getStatus() == SaleStatus.CONFIRMED) {
            throw new BusinessException("Sale is already confirmed.");
        }
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessException("Cancelled sales cannot be confirmed.");
        }

        List<SaleItem> items = saleItemRepository.findAllBySaleIdWithDetails(saleId);
        if (items.isEmpty()) {
            throw new BusinessException("Cannot confirm a sale without items.");
        }

        List<Long> productIds = items.stream()
                .map(SaleItem::getProduct)
                .filter(java.util.Objects::nonNull)
                .map(Product::getId)
                .distinct()
                .toList();
        Map<Long, Product> lockedProducts = new HashMap<>();
        for (Product product : productRepository.findAllByTenantIdAndIdInForUpdate(
                sale.getTenant().getId(),
                productIds)) {
            lockedProducts.put(product.getId(), product);
        }

        for (SaleItem item : items) {
            if (item.isManualEntry() || item.getProduct() == null) {
                continue;
            }
            Product product = lockedProducts.get(item.getProduct().getId());
            if (product == null) {
                throw new ResourceNotFoundException("Product not found for sale item: " + item.getProduct().getId());
            }

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException("No se puede confirmar la venta porque un producto esta inactivo: " + product.getName());
            }

            int availableStock = stockOf(product);
            if (availableStock < item.getQuantity()) {
                throw new BusinessException("Stock insuficiente para " + product.getName() + ". Disponible: " + availableStock + ".");
            }
            item.setProduct(product);
            item.setStore(product.getStore());
        }

        RecalculationSnapshot snapshot = recalculateSale(sale, items);

        for (SaleItem item : snapshot.items()) {
            if (item.isManualEntry() || item.getProduct() == null) {
                continue;
            }
            Product product = lockedProducts.get(item.getProduct().getId());
            int previousStock = product.getStock();
            int newStock = previousStock - item.getQuantity();
            product.setStock(newStock);

            StockMovement movement = new StockMovement();
            movement.setTenant(product.getTenant());
            movement.setProduct(product);
            movement.setStore(product.getStore());
            movement.setType(StockMovementType.SALE);
            movement.setQuantity(-item.getQuantity());
            movement.setPreviousStock(previousStock);
            movement.setNewStock(newStock);
            movement.setReferenceType("SALE");
            movement.setReferenceId(sale.getId());
            stockMovementRepository.save(movement);
        }

        sale.setStatus(SaleStatus.CONFIRMED);
        sale.setConfirmedAt(Instant.now());
        persistRecalculation(sale, snapshot);
        saleRepository.save(sale);
        pickupRepository.findAllByLinkedSaleId(saleId).forEach(pickup -> {
            pickup.setStatus(PickupStatus.COLLECTED);
            pickup.setCollectedAt(sale.getConfirmedAt());
            pickup.setCollectedBy(authenticatedUserService.getCurrentUserSnapshot().user().getEmail());
        });

        return buildResponse(
                sale,
                snapshot.items(),
                snapshot.summaries());
    }

    private PosSaleResponse recalculateSaleTotals(Sale sale) {
        RecalculationSnapshot snapshot = recalculateSale(sale);
        persistRecalculation(sale, snapshot);
        return buildResponse(sale, snapshot.items(), snapshot.summaries());
    }

    private Sale getSaleEntity(Long saleId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Sale sale = saleRepository.findByIdAndTenantId(saleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId));
        requireSaleAccess(sale);
        return sale;
    }

    private Sale getOpenSale(Long saleId) {
        Sale sale = getSaleEntity(saleId);
        if (sale.getStatus() != SaleStatus.OPEN) {
            throw new BusinessException("Only open sales can be modified. Current status: " + sale.getStatus() + ".");
        }
        return sale;
    }

    private Product getProductForSale(Long productId, Long tenantId) {
        Product product = productRepository.findByIdAndTenantIdWithStore(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        accessControlService.requireMarketAccess(product.getStore().getMarket().getId());
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException("Solo puedes agregar productos activos a la venta.");
        }
        return product;
    }

    private void ensureProductStockAvailable(Product product, int requestedQuantity) {
        if (requestedQuantity < 1) {
            throw new BusinessException("La cantidad debe ser al menos 1.");
        }
        int availableStock = stockOf(product);
        if (availableStock <= 0) {
            throw new BusinessException("No puedes agregar " + product.getName() + " porque no tiene stock disponible.");
        }
        if (requestedQuantity > availableStock) {
            throw new BusinessException(
                    "No puedes vender " + requestedQuantity + " unidad(es) de " + product.getName()
                            + ". Stock disponible: " + availableStock + ".");
        }
    }

    private int stockOf(Product product) {
        return product.getStock() == null ? 0 : product.getStock();
    }

    private Store resolveStoreForManualItem(Long storeId, Long tenantId) {
        Store store = storeRepository.findByIdAndTenantId(storeId, tenantId).orElse(null);
        if (store == null) {
            throw new ResourceNotFoundException("No encontramos la Tienda para el cobro manual.");
        }
        accessControlService.requireStoreAccess(store.getId());
        return store;
    }

    private Optional<Sale> resolveExistingOpenSale(Long tenantId, Long marketId) {
        if (marketId != null) {
            Optional<Sale> saleForMarket = saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(
                    tenantId,
                    marketId,
                    SaleStatus.OPEN);
            if (saleForMarket.isPresent()) {
                return saleForMarket;
            }
            return saleRepository.findFirstByTenantIdAndMarketIsNullAndStatusOrderByOpenedAtDesc(tenantId, SaleStatus.OPEN);
        }
        return saleRepository.findFirstByTenantIdAndStatusOrderByOpenedAtDesc(tenantId, SaleStatus.OPEN);
    }

    private Market resolveMarket(Long tenantId, Long marketId) {
        if (marketId == null) {
            return null;
        }
        return marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));
    }

    private void requireSaleAccess(Sale sale) {
        if (sale.getMarket() != null) {
            accessControlService.requireMarketAccess(sale.getMarket().getId());
        }
    }

    private void requirePosWriteAccess() {
        accessControlService.requireAnyRole(RoleCode.ADMIN_MARKET, RoleCode.SELLER);
    }

    private Long resolveAuthenticatedPosMarketId() {
        var currentUser = authenticatedUserService.getCurrentUserSnapshot();
        if (!currentUser.roles().contains(RoleCode.ADMIN_MARKET.name()) && !currentUser.roles().contains(RoleCode.SELLER.name())) {
            throw new BusinessException("Only authorized sales users can operate the POS.");
        }
        if (currentUser.marketIds().size() != 1) {
            throw new BusinessException("The authenticated sales user must be assigned to exactly one tienda to operate the POS.");
        }
        return currentUser.marketIds().getFirst();
    }

    private SaleItem createSaleItem(Sale sale, Product product) {
        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setProduct(product);
        item.setStore(product.getStore());
        item.setProductNameSnapshot(product.getName());
        item.setCollaboratorUserId(product.getOwnerUser() != null ? product.getOwnerUser().getId() : null);
        item.setCollaboratorNameSnapshot(product.getOwnerUser() != null ? product.getOwnerUser().getFullName() : null);
        item.setProductSkuSnapshot(product.getSku());
        item.setProductBarcodeSnapshot(product.getBarcode());
        item.setQuantity(0);
        item.setBaseUnitPrice(ZERO);
        item.setLineBaseSubtotal(ZERO);
        item.setPromotionDiscountAmount(ZERO);
        item.setSubtotal(ZERO);
        item.setPricingType(com.colaborapp.sales.domain.SaleItemPricingType.NORMAL);
        item.setAppliedPromotionId(null);
        item.setAppliedPromotionName(null);
        // Keep commission columns non-null before the pricing engine recalculates the line.
        item.setCommission1Amount(ZERO);
        item.setCommission2Amount(ZERO);
        item.setCommissionIvaAmount(ZERO);
        item.setTotalCommissionAmount(ZERO);
        item.setNetAmount(ZERO);
        return item;
    }

    private SaleItem createManualSaleItem(Sale sale, Store store, PosManualSaleItemRequest request) {
        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setProduct(null);
        item.setStore(store);
        item.setProductNameSnapshot(request.itemName().trim());
        item.setCollaboratorUserId(null);
        item.setCollaboratorNameSnapshot(store.getName());
        item.setProductSkuSnapshot("RETIRO");
        item.setProductBarcodeSnapshot("");
        item.setManualEntry(true);
        item.setManualReference(normalizeOptionalText(request.reference()));
        item.setQuantity(request.quantity());
        item.setBaseUnitPrice(request.amount().setScale(2, HALF_UP));
        item.setLineBaseSubtotal(ZERO);
        item.setPromotionDiscountAmount(ZERO);
        item.setSubtotal(ZERO);
        item.setPricingType(com.colaborapp.sales.domain.SaleItemPricingType.NORMAL);
        item.setAppliedPromotionId(null);
        item.setAppliedPromotionName(normalizeOptionalText(request.description()));
        item.setCommission1Amount(ZERO);
        item.setCommission2Amount(ZERO);
        item.setCommissionIvaAmount(ZERO);
        item.setTotalCommissionAmount(ZERO);
        item.setNetAmount(ZERO);
        return item;
    }

    private RecalculationSnapshot recalculateSale(Sale sale) {
        return recalculateSale(sale, saleItemRepository.findAllBySaleIdWithDetails(sale.getId()));
    }

    private RecalculationSnapshot recalculateSale(Sale sale, List<SaleItem> items) {
        if (items.isEmpty()) {
            sale.setSubtotalAmount(ZERO);
            sale.setTotalDiscountAmount(ZERO);
            sale.setTotalAmount(ZERO);
            sale.setTotalCommissionAmount(ZERO);
            sale.setTotalNetAmount(ZERO);
            sale.setCommissionUfValue(null);
            sale.setCommissionPercentageValue(null);
            return new RecalculationSnapshot(List.of(), List.of());
        }

        Market market = resolveSingleMarket(items);
        if (sale.getMarket() == null) {
            sale.setMarket(market);
        }
        if (sale.getUfValue() == null && sale.getMarket() != null) {
            sale.setUfValue(sale.getMarket().getUfValue());
        }

        BigDecimal ufValue = null;
        if (sale.getPaymentMethod() == PaymentMethod.DEBITO) {
            ufValue = commissionSettingsService.ensureOperationalUfValue(sale.getMarket());
            sale.setUfValue(ufValue);
        }

        PosPricingService.RecalculationResult pricing = posPricingService.calculateSalePricing(items, sale.getPaymentMethod(), ufValue);
        sale.setSubtotalAmount(pricing.subtotalAmount());
        sale.setTotalDiscountAmount(pricing.totalDiscountAmount());
        sale.setTotalAmount(pricing.totalAmount());
        sale.setTotalCommissionAmount(pricing.totalCommissionAmount());
        sale.setTotalNetAmount(pricing.totalNetAmount());
        var effectiveCommission = commissionSettingsService.getEffectiveCommissionConfig(market.getId());
        sale.setCommissionUfValue(effectiveCommission.commissionUfValue());
        sale.setCommissionPercentageValue(effectiveCommission.commissionPercentageValue());

        List<SaleStoreSummary> summaries = pricing.summaries().stream()
                .map(summary -> {
                    summary.setSale(sale);
                    return summary;
                })
                .toList();

        return new RecalculationSnapshot(items, summaries);
    }

    private Market resolveSingleMarket(List<SaleItem> items) {
        Market market = null;
        for (SaleItem item : items) {
            if (item.getStore() == null || item.getStore().getMarket() == null) {
                throw new BusinessException("Cada item de la venta debe pertenecer a una Tienda dentro del Espacio activo.");
            }

            if (item.getProduct() != null && item.getProduct().getStore() != null) {
                item.setStore(item.getProduct().getStore());
            }

            Market currentMarket = item.getStore().getMarket();

            if (market == null) {
                market = currentMarket;
                continue;
            }

            if (!market.getId().equals(currentMarket.getId())) {
                throw new BusinessException("A sale can only contain products from stores in the same market.");
            }
        }

        return market;
    }

    private void persistRecalculation(Sale sale, RecalculationSnapshot snapshot) {
        saleRepository.save(sale);
        if (!snapshot.items().isEmpty()) {
            saleItemRepository.saveAll(snapshot.items());
        }
        List<SaleStoreSummary> existingSummaries = saleStoreSummaryRepository.findAllBySaleIdWithStore(sale.getId());
        Map<Long, SaleStoreSummary> existingByStoreId = new HashMap<>();
        for (SaleStoreSummary existing : existingSummaries) {
            existingByStoreId.put(existing.getStore().getId(), existing);
        }

        List<SaleStoreSummary> summariesToPersist = new java.util.ArrayList<>();
        for (SaleStoreSummary computed : snapshot.summaries()) {
            SaleStoreSummary summary = saleStoreSummaryRepository
                    .findBySaleIdAndStoreId(sale.getId(), computed.getStore().getId())
                    .orElseGet(SaleStoreSummary::new);

            summary.setSale(sale);
            summary.setStore(computed.getStore());
            summary.setLineCount(computed.getLineCount());
            summary.setUnitCount(computed.getUnitCount());
            summary.setSubtotalAmount(computed.getSubtotalAmount());
            summary.setCommission1Amount(computed.getCommission1Amount());
            summary.setCommission2Amount(computed.getCommission2Amount());
            summary.setCommissionIvaAmount(computed.getCommissionIvaAmount());
            summary.setTotalCommissionAmount(computed.getTotalCommissionAmount());
            summary.setNetAmount(computed.getNetAmount());

            existingByStoreId.remove(computed.getStore().getId());
            summariesToPersist.add(summary);
        }

        if (!existingByStoreId.isEmpty()) {
            saleStoreSummaryRepository.deleteAll(existingByStoreId.values());
        }
        if (!summariesToPersist.isEmpty()) {
            saleStoreSummaryRepository.saveAll(summariesToPersist);
        }
    }

    private PosSaleResponse buildResponse(Sale sale, List<SaleItem> items, List<SaleStoreSummary> summaries) {
        TaxBreakdown saleTaxBreakdown = calculateTaxBreakdown(sale.getTotalAmount());
        List<PosSaleItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Long productId = item.getProduct() != null ? item.getProduct().getId() : null;
                    return new PosSaleItemResponse(
                            item.getId(),
                            productId,
                            item.isManualEntry(),
                            item.getManualReference(),
                            item.getStore().getId(),
                            item.getStore().getName(),
                            item.getProductNameSnapshot(),
                            item.getCollaboratorNameSnapshot(),
                            item.getProductSkuSnapshot(),
                            item.getProductBarcodeSnapshot(),
                            item.getQuantity(),
                            item.getProduct() != null ? stockOf(item.getProduct()) : null,
                            item.getBaseUnitPrice(),
                            item.getLineBaseSubtotal(),
                            item.getPromotionDiscountAmount(),
                            item.getSubtotal(),
                            item.getPricingType(),
                            item.getAppliedPromotionId(),
                            item.getAppliedPromotionName(),
                            item.getCommission1Amount(),
                            item.getCommission2Amount(),
                            item.getCommissionIvaAmount(),
                            item.getTotalCommissionAmount(),
                            item.getNetAmount(),
                            item.getPricingType() != null && item.getPricingType() != com.colaborapp.sales.domain.SaleItemPricingType.NORMAL,
                            sale.getUfValue(),
                            sale.getCommissionUfValue(),
                            sale.getCommissionPercentageValue(),
                            item.getNetAmount(),
                            item.getSubtotal());
                })
                .toList();

        List<PosSaleStoreSummaryResponse> summaryResponses = summaries.stream()
                .map(summary -> new PosSaleStoreSummaryResponse(
                        summary.getStore().getId(),
                        summary.getStore().getName(),
                        summary.getLineCount(),
                        summary.getUnitCount(),
                        summary.getSubtotalAmount(),
                        summary.getCommission1Amount(),
                        summary.getCommission2Amount(),
                        summary.getCommissionIvaAmount(),
                        summary.getTotalCommissionAmount(),
                        summary.getNetAmount()))
                .toList();

        return new PosSaleResponse(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getMarket() != null ? sale.getMarket().getId() : null,
                sale.getStatus(),
                sale.getPaymentMethod(),
                saleTaxBreakdown.netAmount(),
                saleTaxBreakdown.ivaAmount(),
                sale.getSubtotalAmount(),
                sale.getTotalDiscountAmount(),
                sale.getTotalAmount(),
                sale.getTotalCommissionAmount(),
                sale.getTotalNetAmount(),
                sale.getUfValue(),
                sale.getCommissionUfValue(),
                sale.getCommissionPercentageValue(),
                sale.getOpenedAt(),
                sale.getConfirmedAt(),
                sale.getCancelledAt(),
                sale.getCancelledBy(),
                sale.getCancellationReason(),
                itemResponses,
                summaryResponses);
    }

    private PosSaleSummaryResponse buildSummaryResponse(Sale sale) {
        Instant dateTime = sale.getConfirmedAt() != null ? sale.getConfirmedAt() : sale.getOpenedAt();
        TaxBreakdown breakdown = calculateTaxBreakdown(sale.getTotalAmount());
        return new PosSaleSummaryResponse(
                sale.getId(),
                sale.getSaleNumber(),
                "POS",
                dateTime,
                sale.getStatus(),
                sale.getSubtotalAmount(),
                breakdown.netAmount(),
                breakdown.ivaAmount(),
                sale.getTotalAmount(),
                sale.getPaymentMethod(),
                sale.getMarket() != null ? sale.getMarket().getId() : null,
                sale.getCreatedBy());
    }

    private TaxBreakdown calculateTaxBreakdown(BigDecimal totalAmount) {
        BigDecimal total = totalAmount == null ? ZERO : totalAmount;
        BigDecimal divisor = new BigDecimal("1.19");
        BigDecimal net = total.divide(divisor, 4, HALF_UP);
        BigDecimal iva = total.subtract(net).setScale(4, HALF_UP);
        return new TaxBreakdown(net, iva);
    }

    private record RecalculationSnapshot(
            List<SaleItem> items,
            List<SaleStoreSummary> summaries) {
    }

    private record TaxBreakdown(
            BigDecimal netAmount,
            BigDecimal ivaAmount) {
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
