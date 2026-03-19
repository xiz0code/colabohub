package com.colaborapp.products.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.inventory.service.InventoryService;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.products.web.dto.ProductCreateRequest;
import com.colaborapp.products.web.dto.ProductPromotionRequest;
import com.colaborapp.products.web.dto.ProductResponse;
import com.colaborapp.products.web.dto.ProductUpdateRequest;
import com.colaborapp.promotions.domain.ProductPromotion;
import com.colaborapp.promotions.domain.PromotionType;
import com.colaborapp.promotions.repository.ProductPromotionRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.stores.service.StoreService;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreService storeService;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private BarcodeGenerator barcodeGenerator;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductPromotionRepository productPromotionRepository;

    @Mock
    private ProductAuditService productAuditService;

    @Mock
    private MarketRepository marketRepository;

    @InjectMocks
    private ProductService productService;

    private Tenant tenant;
    private Store store;
    private User collaborator;
    private Market market;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);

        market = new Market();
        market.setId(14L);
        market.setTenant(tenant);
        market.setName("Sakura Store");

        store = new Store();
        store.setId(2L);
        store.setTenant(tenant);
        store.setMarket(market);
        store.setCode("STOCK-14");
        store.setName("Stock principal");
        store.setStatus(StoreStatus.ACTIVE);
        store.setType(StoreType.STOCK);

        collaborator = new User();
        collaborator.setId(7L);
        collaborator.setFullName("Camila");
        Role collaboratorRole = new Role();
        collaboratorRole.setCode(RoleCode.STORE_USER);
        collaborator.getRoles().add(collaboratorRole);
        collaborator.getStores().add(store);
        collaborator.getMarkets().add(market);
    }

    @Test
    void shouldRejectDuplicateSkuPerStore() {
        ProductCreateRequest request = new ProductCreateRequest(
                2L,
                7L,
                "Producto A",
                "SKU-1",
                "Desc",
                new BigDecimal("15000.00"),
                null,
                3,
                null);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(storeService.getStoreEntity(2L, 1L)).thenReturn(store);
        when(productRepository.existsByStoreIdAndSkuIgnoreCase(2L, "SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ya existe un producto con ese SKU en esta tienda.");
    }

    @Test
    void shouldRejectDuplicatedBarcodeGeneratedInternally() {
        ProductCreateRequest request = new ProductCreateRequest(
                2L,
                7L,
                "Producto A",
                "SKU-1",
                "Desc",
                new BigDecimal("15000.00"),
                null,
                3,
                null);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(storeService.getStoreEntity(2L, 1L)).thenReturn(store);
        when(userRepository.findWithAccessById(7L)).thenReturn(Optional.of(collaborator));
        when(productRepository.existsByStoreIdAndSkuIgnoreCase(2L, "SKU-1")).thenReturn(false);
        when(barcodeGenerator.generateUniqueBarcode()).thenReturn("7500000000007");
        when(productRepository.existsByBarcode("7500000000007")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No pudimos generar un codigo de barras unico. Intenta nuevamente.");

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldCreateProductAndRegisterInitialStockMovement() {
        ProductCreateRequest request = new ProductCreateRequest(
                2L,
                7L,
                "Producto A",
                "SKU-1",
                "Desc",
                new BigDecimal("15000.00"),
                null,
                5,
                new ProductPromotionRequest(PromotionType.QUANTITY_BLOCK, 3, new BigDecimal("4000.00"), null));

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(storeService.getStoreEntity(2L, 1L)).thenReturn(store);
        when(userRepository.findWithAccessById(7L)).thenReturn(Optional.of(collaborator));
        when(productRepository.existsByStoreIdAndSkuIgnoreCase(2L, "SKU-1")).thenReturn(false);
        when(barcodeGenerator.generateUniqueBarcode()).thenReturn("7500000000007");
        when(productRepository.existsByBarcode("7500000000007")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(20L);
            product.setVersion(0L);
            return product;
        });

        var response = productService.createProduct(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        verify(productPromotionRepository).save(any(ProductPromotion.class));
        verify(inventoryService).registerInitialStock(productCaptor.getValue(), 5);
        verify(productAuditService).logChange(productCaptor.getValue(), "Producto creado", null, "Producto A");
        assertThat(productCaptor.getValue().getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(productCaptor.getValue().getOwnerUser()).isEqualTo(collaborator);
        assertThat(response.barcode()).isEqualTo("7500000000007");
        assertThat(response.name()).isEqualTo("Producto A");
    }

    @Test
    void shouldResolveStoreFromAuthenticatedMarketWhenStoreIsNotProvided() {
        ProductCreateRequest request = new ProductCreateRequest(
                null,
                7L,
                "Producto B",
                "SKU-2",
                "Desc",
                new BigDecimal("9000.00"),
                null,
                1,
                null);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.currentMarketIds()).thenReturn(List.of(14L));
        when(marketRepository.findByIdAndTenantId(14L, 1L)).thenReturn(Optional.of(market));
        when(storeRepository.findByMarketIdAndType(14L, StoreType.STOCK)).thenReturn(Optional.of(store));
        when(userRepository.findWithAccessById(7L)).thenReturn(Optional.of(collaborator));
        when(productRepository.existsByStoreIdAndSkuIgnoreCase(2L, "SKU-2")).thenReturn(false);
        when(barcodeGenerator.generateUniqueBarcode()).thenReturn("7500000000099");
        when(productRepository.existsByBarcode("7500000000099")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        verify(storeRepository).findByMarketIdAndType(14L, StoreType.STOCK);
        verify(storeRepository, never()).save(any(Store.class));
    }

    @Test
    void shouldCreateMissingStockStoreAutomatically() {
        ProductCreateRequest request = new ProductCreateRequest(
                null,
                7L,
                "Producto C",
                "SKU-3",
                "Desc",
                new BigDecimal("5000.00"),
                null,
                2,
                null);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.currentMarketIds()).thenReturn(List.of(14L));
        when(marketRepository.findByIdAndTenantId(14L, 1L)).thenReturn(Optional.of(market));
        when(storeRepository.findByMarketIdAndType(14L, StoreType.STOCK)).thenReturn(Optional.empty());
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store createdStore = invocation.getArgument(0);
            createdStore.setId(22L);
            return createdStore;
        });
        when(userRepository.findWithAccessById(7L)).thenReturn(Optional.of(collaborator));
        when(productRepository.existsByStoreIdAndSkuIgnoreCase(22L, "SKU-3")).thenReturn(false);
        when(barcodeGenerator.generateUniqueBarcode()).thenReturn("7500000000199");
        when(productRepository.existsByBarcode("7500000000199")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.createProduct(request);

        assertThat(response.storeId()).isEqualTo(22L);
        verify(storeRepository).save(any(Store.class));
    }

    @Test
    void shouldNotCreateDuplicateStockStoreWhenStockStoreAlreadyExists() {
        ProductCreateRequest request = new ProductCreateRequest(
                null,
                7L,
                "Producto D",
                "SKU-4",
                "Desc",
                new BigDecimal("5000.00"),
                null,
                2,
                null);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.currentMarketIds()).thenReturn(List.of(14L));
        when(marketRepository.findByIdAndTenantId(14L, 1L)).thenReturn(Optional.of(market));
        when(storeRepository.findByMarketIdAndType(14L, StoreType.STOCK)).thenReturn(Optional.of(store));
        when(userRepository.findWithAccessById(7L)).thenReturn(Optional.of(collaborator));
        when(productRepository.existsByStoreIdAndSkuIgnoreCase(2L, "SKU-4")).thenReturn(false);
        when(barcodeGenerator.generateUniqueBarcode()).thenReturn("7500000000299");
        when(productRepository.existsByBarcode("7500000000299")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(request);

        verify(storeRepository, never()).save(any(Store.class));
    }

    @Test
    void shouldCreateAuditTrailWhenUpdatingProduct() {
        Product product = new Product();
        product.setId(20L);
        product.setTenant(tenant);
        product.setStore(store);
        product.setOwnerUser(collaborator);
        product.setName("Producto Inicial");
        product.setSku("SKU-20");
        product.setDescription("Descripcion inicial");
        product.setSalePrice(new BigDecimal("1000.00"));
        product.setStock(20);
        product.setStatus(ProductStatus.ACTIVE);
        product.setBarcode("7500000000011");
        product.setVersion(0L);

        ProductPromotion existingPromotion = new ProductPromotion();
        existingPromotion.setId(31L);
        existingPromotion.setType(PromotionType.QUANTITY_BLOCK);
        existingPromotion.setBlockQuantity(2);
        existingPromotion.setBlockPrice(new BigDecimal("1500.00"));
        existingPromotion.setName("2 x 1500");

        User nextCollaborator = new User();
        nextCollaborator.setId(9L);
        nextCollaborator.setFullName("Lucia");
        Role role = new Role();
        role.setCode(RoleCode.STORE_USER);
        nextCollaborator.getRoles().add(role);
        nextCollaborator.getMarkets().add(store.getMarket());

        ProductUpdateRequest request = new ProductUpdateRequest(
                9L,
                "Producto Final",
                "SKU-20",
                "Descripcion final",
                new BigDecimal("1200.00"),
                null,
                25,
                new ProductPromotionRequest(PromotionType.PERCENTAGE_DISCOUNT, null, null, new BigDecimal("30.00")));

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(productRepository.findByIdAndTenantId(20L, 1L)).thenReturn(Optional.of(product));
        when(userRepository.findWithAccessById(9L)).thenReturn(Optional.of(nextCollaborator));
        when(productRepository.existsByStoreIdAndSkuIgnoreCaseAndIdNot(2L, "SKU-20", 20L)).thenReturn(false);
        when(productPromotionRepository.findFirstByProductIdOrderByIdAsc(20L)).thenReturn(Optional.of(existingPromotion), Optional.of(existingPromotion));

        productService.updateProduct(20L, request);

        verify(inventoryService).adjustStock(any());
        verify(productAuditService).logChange(product, "Nombre del producto", "Producto Inicial", "Producto Final");
        verify(productAuditService).logChange(product, "Precio", new BigDecimal("1000.00"), new BigDecimal("1200.00"));
        verify(productAuditService).logChange(product, "Stock", 20, 25);
        verify(productAuditService).logChange(product, "Promocion", "2 x 1500", "30.00% descuento");
    }
}
