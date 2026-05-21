package com.colaborapp.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.inventory.domain.StockMovement;
import com.colaborapp.inventory.domain.StockMovementType;
import com.colaborapp.inventory.repository.StockMovementRepository;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.pickups.repository.PickupRepository;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.AuthenticatedUserService;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleItem;
import com.colaborapp.sales.domain.SaleItemPricingType;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.domain.SaleStoreSummary;
import com.colaborapp.sales.repository.SaleItemRepository;
import com.colaborapp.sales.repository.SaleRepository;
import com.colaborapp.sales.repository.SaleStoreSummaryRepository;
import com.colaborapp.sales.web.dto.CreatePosSaleRequest;
import com.colaborapp.sales.web.dto.PosManualSaleItemRequest;
import com.colaborapp.sales.web.dto.PosPaymentMethodUpdateRequest;
import com.colaborapp.sales.web.dto.PosSaleItemRequest;
import com.colaborapp.sales.web.dto.PosSaleItemUpdateRequest;
import com.colaborapp.sales.web.dto.PosSaleResponse;
import com.colaborapp.sales.web.dto.PosSaleSummaryResponse;
import com.colaborapp.settings.service.CommissionSettingsService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;

@ExtendWith(MockitoExtension.class)
class PosSaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private SaleStoreSummaryRepository saleStoreSummaryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private PickupRepository pickupRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private SaleNumberGenerator saleNumberGenerator;

    @Mock
    private PosPricingService posPricingService;

    @Mock
    private PosProductLookupService posProductLookupService;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private CommissionSettingsService commissionSettingsService;

    @InjectMocks
    private PosSaleService posSaleService;

    private Tenant tenant;
    private Market marketA;
    private Market marketB;
    private Store storeAna;
    private Store storeLucia;
    private Store storeOtherMarket;
    private Product productAna;
    private Product productAnaTwo;
    private Product productLucia;
    private Product productOtherMarket;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);

        marketA = market(100L, "Mercado Creativo");
        marketB = market(101L, "Otro Mercado");

        storeAna = store(10L, "Tienda Ana", marketA);
        storeLucia = store(11L, "Tienda Lucia", marketA);
        storeOtherMarket = store(12L, "Tienda Externa", marketB);

        productAna = product(1000L, "Aro Flor", "ANA-001", "7500000000101", storeAna, 10, new BigDecimal("12000.00"));
        productAnaTwo = product(1001L, "Pulsera Rosa", "ANA-002", "7500000000102", storeAna, 10, new BigDecimal("15000.00"));
        productLucia = product(1002L, "Collar Luna", "LUC-001", "7500000000103", storeLucia, 10, new BigDecimal("18000.00"));
        productOtherMarket = product(1003L, "Otro Producto", "OTR-001", "7500000000104", storeOtherMarket, 10, new BigDecimal("13000.00"));

        Mockito.lenient().when(authenticatedUserService.getCurrentUserSnapshot()).thenReturn(currentAdminMarketUser());
        Mockito.lenient().when(commissionSettingsService.getEffectiveCommissionConfig(marketA.getId())).thenReturn(defaultCommissionConfig());
        Mockito.lenient().when(commissionSettingsService.getEffectiveCommissionConfig(marketB.getId())).thenReturn(defaultCommissionConfig());
        Mockito.lenient().when(pickupRepository.findAllByLinkedSaleId(any())).thenReturn(List.of());
    }

    @Test
    void shouldCreateOpenSale() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(1L, marketA.getId(), SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(saleRepository.findFirstByTenantIdAndMarketIsNullAndStatusOrderByOpenedAtDesc(1L, SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(marketA.getId(), 1L)).thenReturn(Optional.of(marketA));
        when(saleNumberGenerator.next()).thenReturn("S-2026-00000001");
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            sale.setId(1L);
            return sale;
        });

        PosSaleResponse response = posSaleService.createSale(new CreatePosSaleRequest(PaymentMethod.CASH, null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(SaleStatus.OPEN);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.ufValue()).isEqualByComparingTo("36500.00");
        assertThat(response.marketId()).isEqualTo(marketA.getId());
        assertThat(response.items()).isEmpty();
    }

    @Test
    void shouldListSalesWithNetAndIva() {
        Sale sale = openSale(88L);
        sale.setMarket(marketA);
        sale.setTotalAmount(new BigDecimal("11900.00"));

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findTop100ByTenantIdAndMarketIdOrderByOpenedAtDescIdDesc(1L, marketA.getId()))
                .thenReturn(List.of(sale));

        List<PosSaleSummaryResponse> response = posSaleService.listSales();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().marketId()).isEqualTo(marketA.getId());
        assertThat(response.getFirst().ivaAmount()).isPositive();
        assertThat(response.getFirst().paymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void storeUserCannotCreateSale() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("You do not have permission to perform this action."))
                .when(accessControlService)
                .requireAnyRole(RoleCode.ADMIN_MARKET, RoleCode.SELLER);

        assertThatThrownBy(() -> posSaleService.createSale(new CreatePosSaleRequest(PaymentMethod.CASH, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission to perform this action.");
    }

    @Test
    void sellerCanCreateSaleForAssignedMarket() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(authenticatedUserService.getCurrentUserSnapshot()).thenReturn(currentSellerUser());
        when(saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(1L, marketA.getId(), SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(saleRepository.findFirstByTenantIdAndMarketIsNullAndStatusOrderByOpenedAtDesc(1L, SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(marketA.getId(), 1L)).thenReturn(Optional.of(marketA));
        when(saleNumberGenerator.next()).thenReturn("S-2026-00000077");
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            sale.setId(77L);
            return sale;
        });

        PosSaleResponse response = posSaleService.createSale(new CreatePosSaleRequest(PaymentMethod.CASH, null));

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.marketId()).isEqualTo(marketA.getId());
    }

    @Test
    void shouldReturnExistingOpenSaleInsteadOfCreatingAnotherOneForSameMarket() {
        Sale existingSale = openSale(44L);
        existingSale.setMarket(marketA);
        SaleItem item = saleItem(existingSale, productAna, 1, "12000.00");
        SaleStoreSummary summary = summary(existingSale, storeAna, 1, 1, "12000.00", "0.00", "12000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(1L, marketA.getId(), SaleStatus.OPEN))
                .thenReturn(Optional.of(existingSale));
        when(saleItemRepository.findAllBySaleIdWithDetails(44L)).thenReturn(List.of(item));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(44L)).thenReturn(List.of(summary));

        PosSaleResponse response = posSaleService.createSale(new CreatePosSaleRequest(PaymentMethod.CREDIT, marketA.getId()));

        assertThat(response.id()).isEqualTo(44L);
        assertThat(response.status()).isEqualTo(SaleStatus.OPEN);
        verify(saleRepository, never()).save(any(Sale.class));
    }

    @Test
    void shouldCreateNewOpenSaleWhenAnotherMarketAlreadyHasOne() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(1L, marketA.getId(), SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(saleRepository.findFirstByTenantIdAndMarketIsNullAndStatusOrderByOpenedAtDesc(1L, SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(marketA.getId(), 1L)).thenReturn(Optional.of(marketA));
        when(saleNumberGenerator.next()).thenReturn("S-2026-00000002");
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            sale.setId(51L);
            return sale;
        });

        PosSaleResponse response = posSaleService.createSale(new CreatePosSaleRequest(PaymentMethod.CASH, marketA.getId()));

        assertThat(response.id()).isEqualTo(51L);
        verify(saleRepository, never()).findFirstByTenantIdAndStatusOrderByOpenedAtDesc(1L, SaleStatus.OPEN);
    }

    @Test
    void shouldDenyOperationWhenSaleBelongsToAnotherMarket() {
        Sale sale = openSale(99L);
        sale.setMarket(marketA);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.of(sale));
        org.mockito.Mockito.doThrow(new AccessDeniedException("You do not have permission to perform this action."))
                .when(accessControlService).requireMarketAccess(marketA.getId());

        assertThatThrownBy(() -> posSaleService.confirm(99L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission to perform this action.");
    }

    @Test
    void shouldAddItemToOpenSaleAndPopulateSnapshots() {
        Sale sale = openSale(1L);
        SaleItem persistedItem = saleItem(sale, productAna, 1, "12000.00");
        SaleStoreSummary summary = summary(sale, storeAna, 1, 1, "12000.00", "0.00", "12000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(productRepository.findByIdAndTenantIdWithStore(productAna.getId(), 1L)).thenReturn(Optional.of(productAna));
        when(saleItemRepository.findBySaleIdAndProductId(1L, productAna.getId())).thenReturn(Optional.empty());
        when(saleItemRepository.save(any(SaleItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(persistedItem));
        when(posPricingService.calculateSalePricing(any(), eq(PaymentMethod.CASH), eq(null)))
                .thenReturn(new PosPricingService.RecalculationResult(
                        new BigDecimal("12000.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("12000.00"),
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("12000.00"),
                        List.of(summary)));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(1L)).thenReturn(List.of());
        when(saleStoreSummaryRepository.findBySaleIdAndStoreId(1L, storeAna.getId())).thenReturn(Optional.empty());

        PosSaleResponse response = posSaleService.addItem(1L, new PosSaleItemRequest(productAna.getId(), 1));

        ArgumentCaptor<SaleItem> itemCaptor = ArgumentCaptor.forClass(SaleItem.class);
        verify(saleItemRepository).save(itemCaptor.capture());
        SaleItem saved = itemCaptor.getValue();
        assertThat(saved.getProductNameSnapshot()).isEqualTo("Aro Flor");
        assertThat(saved.getProductSkuSnapshot()).isEqualTo("ANA-001");
        assertThat(saved.getProductBarcodeSnapshot()).isEqualTo("0001000");
        assertThat(saved.getStore()).isEqualTo(storeAna);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void shouldAddManualItemUsingCollaboratorSnapshotWhenProvided() {
        Sale sale = openSale(1L);
        SaleItem persistedItem = manualSaleItem(sale, storeAna, "Retiro BW2", "pickup:3", "Butterfly", 22L, "7900.00");
        SaleStoreSummary summary = summary(sale, storeAna, 1, 1, "7900.00", "0.00", "7900.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(storeRepository.findByIdAndTenantId(storeAna.getId(), 1L)).thenReturn(Optional.of(storeAna));
        when(saleItemRepository.findBySaleIdAndManualReference(1L, "pickup:3")).thenReturn(Optional.empty());
        when(saleItemRepository.save(any(SaleItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(persistedItem));
        when(posPricingService.calculateSalePricing(any(), eq(PaymentMethod.CASH), eq(null)))
                .thenReturn(new PosPricingService.RecalculationResult(
                        new BigDecimal("7900.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("7900.00"),
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("7900.00"),
                        List.of(summary)));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(1L)).thenReturn(List.of());
        when(saleStoreSummaryRepository.findBySaleIdAndStoreId(1L, storeAna.getId())).thenReturn(Optional.empty());

        PosSaleResponse response = posSaleService.addManualItem(
                1L,
                new PosManualSaleItemRequest(
                        storeAna.getId(),
                        "Retiro BW2",
                        "posters, sets",
                        new BigDecimal("7900.00"),
                        "pickup:3",
                        1,
                        22L,
                        "Butterfly"));

        ArgumentCaptor<SaleItem> itemCaptor = ArgumentCaptor.forClass(SaleItem.class);
        verify(saleItemRepository).save(itemCaptor.capture());
        SaleItem saved = itemCaptor.getValue();
        assertThat(saved.getCollaboratorUserId()).isEqualTo(22L);
        assertThat(saved.getCollaboratorNameSnapshot()).isEqualTo("Butterfly");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().collaboratorName()).isEqualTo("Butterfly");
    }

    @Test
    void shouldRejectAddingProductWithoutStock() {
        productAna.setStock(0);
        Sale sale = openSale(1L);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(productRepository.findByIdAndTenantIdWithStore(productAna.getId(), 1L)).thenReturn(Optional.of(productAna));
        when(saleItemRepository.findBySaleIdAndProductId(1L, productAna.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> posSaleService.addItem(1L, new PosSaleItemRequest(productAna.getId(), 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No puedes agregar Aro Flor porque no tiene stock disponible.");

        verify(saleItemRepository, never()).save(any(SaleItem.class));
    }

    @Test
    void shouldRejectAddingMoreUnitsThanAvailableStock() {
        productAna.setStock(1);
        Sale sale = openSale(1L);
        SaleItem existingItem = saleItem(sale, productAna, 1, "12000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(productRepository.findByIdAndTenantIdWithStore(productAna.getId(), 1L)).thenReturn(Optional.of(productAna));
        when(saleItemRepository.findBySaleIdAndProductId(1L, productAna.getId())).thenReturn(Optional.of(existingItem));

        assertThatThrownBy(() -> posSaleService.addItem(1L, new PosSaleItemRequest(productAna.getId(), 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No puedes vender 2 unidad(es) de Aro Flor. Stock disponible: 1.");

        verify(saleItemRepository, never()).save(any(SaleItem.class));
    }

    @Test
    void shouldNotCreateDuplicateStoreSummaryWhenAddingSecondItemFromSameStore() {
        Sale sale = openSale(1L);
        SaleItem itemOne = saleItem(sale, productAna, 1, "12000.00");
        SaleItem itemTwo = saleItem(sale, productAnaTwo, 1, "15000.00");
        SaleStoreSummary existingSummary = summary(sale, storeAna, 1, 1, "12000.00", "0.00", "12000.00");
        existingSummary.setId(90L);
        SaleStoreSummary computedSummary = summary(sale, storeAna, 2, 2, "27000.00", "0.00", "27000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(productRepository.findByIdAndTenantIdWithStore(productAnaTwo.getId(), 1L)).thenReturn(Optional.of(productAnaTwo));
        when(saleItemRepository.findBySaleIdAndProductId(1L, productAnaTwo.getId())).thenReturn(Optional.empty());
        when(saleItemRepository.save(any(SaleItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(itemOne, itemTwo));
        when(posPricingService.calculateSalePricing(any(), eq(PaymentMethod.CASH), eq(null)))
                .thenReturn(new PosPricingService.RecalculationResult(
                        new BigDecimal("27000.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("27000.00"),
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("27000.00"),
                        List.of(computedSummary)));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(1L)).thenReturn(List.of(existingSummary));
        when(saleStoreSummaryRepository.findBySaleIdAndStoreId(1L, storeAna.getId())).thenReturn(Optional.of(existingSummary));

        assertThatCode(() -> posSaleService.addItem(1L, new PosSaleItemRequest(productAnaTwo.getId(), 1)))
                .doesNotThrowAnyException();

        ArgumentCaptor<List<SaleStoreSummary>> captor = ArgumentCaptor.forClass(List.class);
        verify(saleStoreSummaryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getStore()).isEqualTo(storeAna);
    }

    @Test
    void shouldAllowItemsFromMultipleStoresInSameMarket() {
        Sale sale = openSale(1L);
        SaleItem anaItem = saleItem(sale, productAna, 1, "12000.00");
        SaleItem luciaItem = saleItem(sale, productLucia, 1, "18000.00");
        SaleStoreSummary anaSummary = summary(sale, storeAna, 1, 1, "12000.00", "0.00", "12000.00");
        SaleStoreSummary luciaSummary = summary(sale, storeLucia, 1, 1, "18000.00", "0.00", "18000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(anaItem, luciaItem));
        when(posPricingService.calculateSalePricing(any(), eq(PaymentMethod.CASH), eq(null)))
                .thenReturn(new PosPricingService.RecalculationResult(
                        new BigDecimal("30000.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("30000.00"),
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("30000.00"),
                        List.of(anaSummary, luciaSummary)));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(1L)).thenReturn(List.of());
        when(saleStoreSummaryRepository.findBySaleIdAndStoreId(1L, storeAna.getId())).thenReturn(Optional.empty());
        when(saleStoreSummaryRepository.findBySaleIdAndStoreId(1L, storeLucia.getId())).thenReturn(Optional.empty());

        PosSaleResponse response = posSaleService.recalculate(1L);

        assertThat(response.storeSummaries()).hasSize(2);
        assertThat(sale.getMarket()).isEqualTo(marketA);
    }

    @Test
    void shouldRejectItemsFromDifferentMarkets() {
        Sale sale = openSale(1L);
        SaleItem anaItem = saleItem(sale, productAna, 1, "12000.00");
        SaleItem otherMarketItem = saleItem(sale, productOtherMarket, 1, "13000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(anaItem, otherMarketItem));

        assertThatThrownBy(() -> posSaleService.recalculate(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A sale can only contain products from stores in the same market.");
    }

    @Test
    void shouldConfirmSaleAndDiscountStockAndCreateMovements() {
        Sale sale = openSale(1L);
        SaleItem item = saleItem(sale, productAna, 2, "24000.00");
        SaleStoreSummary computedSummary = summary(sale, storeAna, 1, 2, "24000.00", "0.00", "24000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(item));
        when(productRepository.findAllByTenantIdAndIdInForUpdate(1L, List.of(productAna.getId()))).thenReturn(List.of(productAna));
        when(posPricingService.calculateSalePricing(any(), eq(PaymentMethod.CASH), eq(null)))
                .thenReturn(new PosPricingService.RecalculationResult(
                        new BigDecimal("24000.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("24000.00"),
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("24000.00"),
                        List.of(computedSummary)));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(1L)).thenReturn(List.of());
        when(saleStoreSummaryRepository.findBySaleIdAndStoreId(1L, storeAna.getId())).thenReturn(Optional.empty());

        PosSaleResponse response = posSaleService.confirm(1L);

        assertThat(response.status()).isEqualTo(SaleStatus.CONFIRMED);
        assertThat(sale.getConfirmedAt()).isNotNull();
        assertThat(productAna.getStock()).isEqualTo(8);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(StockMovementType.SALE);
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(-2);
        assertThat(movementCaptor.getValue().getReferenceType()).isEqualTo("SALE");
    }

    @Test
    void shouldCancelOpenSaleWithoutRestoringStock() {
        Sale sale = openSale(1L);
        SaleItem item = saleItem(sale, productAna, 1, "12000.00");
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(item));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(1L)).thenReturn(List.of());

        PosSaleResponse response = posSaleService.cancel(1L, "Cliente se arrepintio");

        assertThat(response.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(productAna.getStock()).isEqualTo(10);
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void shouldCancelConfirmedSaleAndRestoreStock() {
        Sale sale = openSale(1L);
        sale.setStatus(SaleStatus.CONFIRMED);
        sale.setConfirmedAt(Instant.now());
        productAna.setStock(8);
        SaleItem item = saleItem(sale, productAna, 2, "24000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(item));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(1L)).thenReturn(List.of());
        when(productRepository.findAllByTenantIdAndIdInForUpdate(1L, List.of(productAna.getId()))).thenReturn(List.of(productAna));

        PosSaleResponse response = posSaleService.cancel(1L, "Cliente solicito anulacion");

        assertThat(response.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(productAna.getStock()).isEqualTo(10);
        assertThat(response.cancellationReason()).isEqualTo("Cliente solicito anulacion");
        assertThat(response.cancelledBy()).isEqualTo("admin.tienda@colaborapp.cl");

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(StockMovementType.ADJUSTMENT);
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(2);
        assertThat(movementCaptor.getValue().getReferenceType()).isEqualTo("SALE_CANCEL");
    }

    @Test
    void shouldRequireReasonToCancelConfirmedSale() {
        Sale sale = openSale(1L);
        sale.setStatus(SaleStatus.CONFIRMED);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> posSaleService.cancel(1L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ingresa un motivo para anular la venta.");
    }

    @Test
    void cannotEditConfirmedSale() {
        Sale sale = openSale(10L);
        sale.setStatus(SaleStatus.CONFIRMED);
        sale.setMarket(marketA);
        productAna.setStock(8);
        SaleItem item = saleItem(sale, productAna, 2, "24000.00");
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(sale));
        when(saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(1L, marketA.getId(), SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(saleRepository.findFirstByTenantIdAndMarketIsNullAndStatusOrderByOpenedAtDesc(1L, SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(saleItemRepository.findAllBySaleIdWithDetails(10L)).thenReturn(List.of(item));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(10L)).thenReturn(List.of());
        when(productRepository.findAllByTenantIdAndIdInForUpdate(1L, List.of(productAna.getId()))).thenReturn(List.of(productAna));

        PosSaleResponse response = posSaleService.editSale(10L);

        assertThat(response.status()).isEqualTo(SaleStatus.OPEN);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.OPEN);
        assertThat(sale.getConfirmedAt()).isNull();
        assertThat(productAna.getStock()).isEqualTo(10);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getReferenceType()).isEqualTo("SALE_EDIT_REOPEN");
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    void cannotEditCancelledSale() {
        Sale sale = openSale(11L);
        sale.setStatus(SaleStatus.CANCELLED);
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(11L, 1L)).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> posSaleService.editSale(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cancelled sales cannot be edited.");
    }

    @Test
    void shouldBlockEditingConfirmedSaleWhenAnotherOpenSaleExists() {
        Sale confirmedSale = openSale(15L);
        confirmedSale.setStatus(SaleStatus.CONFIRMED);
        confirmedSale.setMarket(marketA);
        Sale otherOpenSale = openSale(16L);
        otherOpenSale.setMarket(marketA);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(15L, 1L)).thenReturn(Optional.of(confirmedSale));
        when(saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(1L, marketA.getId(), SaleStatus.OPEN))
                .thenReturn(Optional.of(otherOpenSale));

        assertThatThrownBy(() -> posSaleService.editSale(15L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ya existe otra venta abierta en este Espacio. Cierrala o anulala antes de editar una venta confirmada.");
    }

    @Test
    void shouldRecoverAutomaticUfWhenDebitSaleNeedsIt() {
        Sale sale = openSale(31L);
        sale.setMarket(marketA);
        sale.setUfValue(null);
        sale.setPaymentMethod(PaymentMethod.CASH);
        SaleItem item = saleItem(sale, productAna, 1, "12000.00");
        SaleStoreSummary summary = summary(sale, storeAna, 1, 1, "12000.00", "175.00", "11825.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(31L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findAllBySaleIdWithDetails(31L)).thenReturn(List.of(item));
        when(commissionSettingsService.ensureOperationalUfValue(marketA)).thenReturn(new BigDecimal("38999.00"));
        when(posPricingService.calculateSalePricing(any(), eq(PaymentMethod.DEBITO), eq(new BigDecimal("38999.00"))))
                .thenReturn(new PosPricingService.RecalculationResult(
                        new BigDecimal("12000.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("12000.00"),
                        new BigDecimal("175"),
                        new BigDecimal("11825.00"),
                        List.of(summary)));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(31L)).thenReturn(List.of());
        when(saleStoreSummaryRepository.findBySaleIdAndStoreId(31L, storeAna.getId())).thenReturn(Optional.empty());

        PosSaleResponse response = posSaleService.updatePaymentMethod(31L, new PosPaymentMethodUpdateRequest(PaymentMethod.DEBITO));

        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.DEBITO);
        assertThat(response.ufValue()).isEqualByComparingTo("38999.00");
        assertThat(sale.getUfValue()).isEqualByComparingTo("38999.00");
    }

    @Test
    void shouldFailDebitSaleWhenAutomaticUfCannotBeRecovered() {
        Sale sale = openSale(32L);
        sale.setMarket(marketA);
        sale.setUfValue(null);
        SaleItem item = saleItem(sale, productAna, 1, "12000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(32L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findAllBySaleIdWithDetails(32L)).thenReturn(List.of(item));
        when(commissionSettingsService.ensureOperationalUfValue(marketA))
                .thenThrow(new BusinessException("No pudimos sincronizar automaticamente la UF para esta Tienda. Intenta nuevamente en unos minutos o define la UF manualmente."));

        assertThatThrownBy(() -> posSaleService.updatePaymentMethod(32L, new PosPaymentMethodUpdateRequest(PaymentMethod.DEBITO)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No pudimos sincronizar automaticamente la UF para esta Tienda. Intenta nuevamente en unos minutos o define la UF manualmente.");
    }

    @Test
    void shouldReturnCurrentOpenSale() {
        Sale sale = openSale(20L);
        sale.setMarket(marketA);
        SaleItem item = saleItem(sale, productAna, 1, "12000.00");
        SaleStoreSummary summary = summary(sale, storeAna, 1, 1, "12000.00", "0.00", "12000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(1L, marketA.getId(), SaleStatus.OPEN))
                .thenReturn(Optional.of(sale));
        when(saleItemRepository.findAllBySaleIdWithDetails(20L)).thenReturn(List.of(item));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(20L)).thenReturn(List.of(summary));

        PosSaleResponse response = posSaleService.getOpenSaleOrNull(marketA.getId());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo(SaleStatus.OPEN);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void shouldUpdateItemQuantityAndRecalculate() {
        Sale sale = openSale(1L);
        SaleItem item = saleItem(sale, productAna, 1, "12000.00");
        SaleStoreSummary summary = summary(sale, storeAna, 1, 3, "36000.00", "0.00", "36000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findByIdAndSaleId(50L, 1L)).thenReturn(Optional.of(item));
        when(productRepository.findByIdAndTenantIdWithStore(productAna.getId(), 1L)).thenReturn(Optional.of(productAna));
        when(saleItemRepository.findAllBySaleIdWithDetails(1L)).thenReturn(List.of(item));
        when(posPricingService.calculateSalePricing(any(), eq(PaymentMethod.CASH), eq(null)))
                .thenReturn(new PosPricingService.RecalculationResult(
                        new BigDecimal("36000.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("36000.00"),
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("36000.00"),
                        List.of(summary)));
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(1L)).thenReturn(List.of());
        when(saleStoreSummaryRepository.findBySaleIdAndStoreId(1L, storeAna.getId())).thenReturn(Optional.empty());

        PosSaleResponse response = posSaleService.updateItem(1L, 50L, new PosSaleItemUpdateRequest(3));

        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("36000.00");
    }

    @Test
    void shouldRejectUpdatingItemBeyondAvailableStock() {
        productAna.setStock(1);
        Sale sale = openSale(1L);
        SaleItem item = saleItem(sale, productAna, 1, "12000.00");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findByIdAndSaleId(50L, 1L)).thenReturn(Optional.of(item));
        when(productRepository.findByIdAndTenantIdWithStore(productAna.getId(), 1L)).thenReturn(Optional.of(productAna));

        assertThatThrownBy(() -> posSaleService.updateItem(1L, 50L, new PosSaleItemUpdateRequest(2)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No puedes vender 2 unidad(es) de Aro Flor. Stock disponible: 1.");

        verify(saleItemRepository, never()).save(any(SaleItem.class));
    }

    @Test
    void shouldReuseLegacyOpenSaleWithoutMarketAndAssignRequestedMarket() {
        Sale legacySale = openSale(60L);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findFirstByTenantIdAndMarketIdAndStatusOrderByOpenedAtDesc(1L, marketA.getId(), SaleStatus.OPEN))
                .thenReturn(Optional.empty());
        when(saleRepository.findFirstByTenantIdAndMarketIsNullAndStatusOrderByOpenedAtDesc(1L, SaleStatus.OPEN))
                .thenReturn(Optional.of(legacySale));
        when(marketRepository.findByIdAndTenantId(marketA.getId(), 1L)).thenReturn(Optional.of(marketA));
        when(saleItemRepository.findAllBySaleIdWithDetails(60L)).thenReturn(List.of());
        when(saleStoreSummaryRepository.findAllBySaleIdWithStore(60L)).thenReturn(List.of());

        PosSaleResponse response = posSaleService.createSale(new CreatePosSaleRequest(PaymentMethod.CASH, marketA.getId()));

        assertThat(response.id()).isEqualTo(60L);
        assertThat(legacySale.getMarket()).isEqualTo(marketA);
        verify(saleRepository).save(legacySale);
    }

    @Test
    void confirmingConfirmedSaleShouldBeSafe() {
        Sale sale = openSale(70L);
        sale.setStatus(SaleStatus.CONFIRMED);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(70L, 1L)).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> posSaleService.confirm(70L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Sale is already confirmed.");
    }

    @Test
    void confirmingCancelledSaleShouldBeBlockedClearly() {
        Sale sale = openSale(71L);
        sale.setStatus(SaleStatus.CANCELLED);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(71L, 1L)).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> posSaleService.confirm(71L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cancelled sales cannot be confirmed.");
    }

    @Test
    void cancellingCancelledSaleShouldBeSafe() {
        Sale sale = openSale(72L);
        sale.setStatus(SaleStatus.CANCELLED);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(saleRepository.findByIdAndTenantId(72L, 1L)).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> posSaleService.cancel(72L, "Duplicado"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Sale is already cancelled.");
    }

    private Sale openSale(Long id) {
        Sale sale = new Sale();
        sale.setId(id);
        sale.setTenant(tenant);
        sale.setSaleNumber("S-2026-00000001");
        sale.setStatus(SaleStatus.OPEN);
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setSubtotalAmount(BigDecimal.ZERO.setScale(2));
        sale.setTotalDiscountAmount(BigDecimal.ZERO.setScale(2));
        sale.setTotalAmount(BigDecimal.ZERO.setScale(2));
        sale.setTotalCommissionAmount(BigDecimal.ZERO.setScale(0));
        sale.setTotalNetAmount(BigDecimal.ZERO.setScale(2));
        sale.setOpenedAt(Instant.now());
        return sale;
    }

    private Market market(Long id, String name) {
        Market market = new Market();
        market.setId(id);
        market.setTenant(tenant);
        market.setName(name);
        market.setEmail(name.toLowerCase().replace(" ", "") + "@mail.cl");
        market.setCity("Santiago");
        market.setCurrency("CLP");
        market.setUfEnabled(true);
        market.setUfValue(new BigDecimal("36500.00"));
        return market;
    }

    private Store store(Long id, String name, Market market) {
        Store store = new Store();
        store.setId(id);
        store.setTenant(tenant);
        store.setMarket(market);
        store.setCode(name.toUpperCase().replace(" ", "-"));
        store.setName(name);
        store.setType(StoreType.COLLABORATOR);
        store.setStatus(StoreStatus.ACTIVE);
        return store;
    }

    private Product product(Long id, String name, String sku, String barcode, Store store, int stock, BigDecimal salePrice) {
        Product product = new Product();
        product.setId(id);
        product.setTenant(tenant);
        product.setStore(store);
        product.setName(name);
        product.setSku(sku);
        product.setShortBarcode(String.format("%07d", id));
        product.setBarcode(barcode);
        product.setStatus(ProductStatus.ACTIVE);
        product.setStock(stock);
        product.setSalePrice(salePrice);
        product.setVersion(0L);
        return product;
    }

    private SaleItem saleItem(Sale sale, Product product, int quantity, String subtotal) {
        SaleItem item = new SaleItem();
        item.setId(product.getId() + 100L);
        item.setSale(sale);
        item.setProduct(product);
        item.setStore(product.getStore());
        item.setProductNameSnapshot(product.getName());
        item.setProductSkuSnapshot(product.getSku());
        item.setProductBarcodeSnapshot(product.getShortBarcode());
        item.setQuantity(quantity);
        item.setBaseUnitPrice(product.getSalePrice());
        item.setLineBaseSubtotal(new BigDecimal(subtotal));
        item.setPromotionDiscountAmount(BigDecimal.ZERO.setScale(2));
        item.setSubtotal(new BigDecimal(subtotal));
        item.setPricingType(SaleItemPricingType.NORMAL);
        item.setCommission1Amount(BigDecimal.ZERO.setScale(0));
        item.setCommission2Amount(BigDecimal.ZERO.setScale(0));
        item.setCommissionIvaAmount(BigDecimal.ZERO.setScale(0));
        item.setTotalCommissionAmount(BigDecimal.ZERO.setScale(0));
        item.setNetAmount(new BigDecimal(subtotal));
        return item;
    }

    private SaleItem manualSaleItem(
            Sale sale,
            Store store,
            String productName,
            String reference,
            String collaboratorName,
            Long collaboratorUserId,
            String subtotal) {
        SaleItem item = new SaleItem();
        item.setId(9000L);
        item.setSale(sale);
        item.setProduct(null);
        item.setStore(store);
        item.setManualEntry(true);
        item.setManualReference(reference);
        item.setProductNameSnapshot(productName);
        item.setCollaboratorNameSnapshot(collaboratorName);
        item.setCollaboratorUserId(collaboratorUserId);
        item.setProductSkuSnapshot("RETIRO");
        item.setProductBarcodeSnapshot("");
        item.setQuantity(1);
        item.setBaseUnitPrice(new BigDecimal(subtotal));
        item.setLineBaseSubtotal(new BigDecimal(subtotal));
        item.setPromotionDiscountAmount(BigDecimal.ZERO.setScale(2));
        item.setSubtotal(new BigDecimal(subtotal));
        item.setPricingType(SaleItemPricingType.NORMAL);
        item.setCommission1Amount(BigDecimal.ZERO.setScale(0));
        item.setCommission2Amount(BigDecimal.ZERO.setScale(0));
        item.setCommissionIvaAmount(BigDecimal.ZERO.setScale(0));
        item.setTotalCommissionAmount(BigDecimal.ZERO.setScale(0));
        item.setNetAmount(new BigDecimal(subtotal));
        return item;
    }

    private SaleStoreSummary summary(Sale sale, Store store, int lineCount, int unitCount, String subtotal, String commission, String net) {
        SaleStoreSummary summary = new SaleStoreSummary();
        summary.setSale(sale);
        summary.setStore(store);
        summary.setLineCount(lineCount);
        summary.setUnitCount(unitCount);
        summary.setSubtotalAmount(new BigDecimal(subtotal));
        summary.setCommission1Amount(BigDecimal.ZERO.setScale(0));
        summary.setCommission2Amount(BigDecimal.ZERO.setScale(0));
        summary.setCommissionIvaAmount(BigDecimal.ZERO.setScale(0));
        summary.setTotalCommissionAmount(new BigDecimal(commission));
        summary.setNetAmount(new BigDecimal(net));
        return summary;
    }

    private AuthenticatedUserService.CurrentAuthenticatedUser currentAdminMarketUser() {
        User user = new User();
        user.setId(500L);
        user.setEmail("admin.tienda@colaborapp.cl");
        user.setFullName("Admin Tienda");
        user.setActive(true);
        return new AuthenticatedUserService.CurrentAuthenticatedUser(
                user,
                List.of(RoleCode.ADMIN_MARKET.name()),
                true,
                marketA.getId(),
                marketA.getName(),
                List.of(marketA.getId()),
                List.of(),
                List.of(marketA.getName()));
    }

    private AuthenticatedUserService.CurrentAuthenticatedUser currentSellerUser() {
        User user = new User();
        user.setId(501L);
        user.setEmail("seller@colaborapp.cl");
        user.setFullName("Vendedor");
        user.setActive(true);
        return new AuthenticatedUserService.CurrentAuthenticatedUser(
                user,
                List.of(RoleCode.SELLER.name()),
                true,
                marketA.getId(),
                marketA.getName(),
                List.of(marketA.getId()),
                List.of(),
                List.of(marketA.getName()));
    }

    private CommissionSettingsService.EffectiveCommissionConfig defaultCommissionConfig() {
        return new CommissionSettingsService.EffectiveCommissionConfig(
                CommissionSettingsService.DEFAULT_COMMISSION_UF,
                CommissionSettingsService.DEFAULT_COMMISSION_PERCENTAGE);
    }
}
