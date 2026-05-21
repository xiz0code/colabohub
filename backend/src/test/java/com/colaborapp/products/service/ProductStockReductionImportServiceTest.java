package com.colaborapp.products.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.inventory.service.InventoryService;
import com.colaborapp.inventory.web.dto.StockAdjustmentRequest;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.tenant.domain.Tenant;

@ExtendWith(MockitoExtension.class)
class ProductStockReductionImportServiceTest {

    @Mock
    private ProductImportService productImportService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ProductStockReductionImportService service;
    private Tenant tenant;
    private Product product;

    @BeforeEach
    void setUp() throws Exception {
        TransactionTemplate passthroughTransactionTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        service = new ProductStockReductionImportService(
                productImportService,
                productRepository,
                inventoryService,
                currentTenantProvider,
                accessControlService,
                passthroughTransactionTemplate);

        tenant = new Tenant();
        tenant.setId(1L);

        Market market = new Market();
        market.setId(10L);
        market.setTenant(tenant);

        Store store = new Store();
        store.setId(20L);
        store.setTenant(tenant);
        store.setMarket(market);

        product = new Product();
        product.setId(30L);
        product.setTenant(tenant);
        product.setStore(store);
        product.setShortBarcode("0000030");
        product.setBarcode("7501234567890");
        product.setStatus(ProductStatus.ACTIVE);

        lenient().when(productImportService.decodeCsv(any())).thenAnswer(invocation -> {
            MockMultipartFile file = invocation.getArgument(0);
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        });
        lenient().when(productImportService.parseCsvLine(any())).thenCallRealMethod();
    }

    @Test
    void shouldReduceStockByProductCode() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(productRepository.findByBarcodeForPos(eq(1L), any(String.class), eq(ProductStatus.ACTIVE))).thenReturn(Optional.of(product));
        when(productImportService.parseCsvLine("0000030,2")).thenReturn(List.of("0000030", "2"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "reduccion.csv",
                "text/csv",
                "codigo_producto,cantidad\n0000030,2".getBytes(StandardCharsets.UTF_8));

        var response = service.importCsv(file);

        ArgumentCaptor<StockAdjustmentRequest> requestCaptor = ArgumentCaptor.forClass(StockAdjustmentRequest.class);
        verify(inventoryService).adjustStock(requestCaptor.capture());
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.errorCount()).isZero();
        assertThat(requestCaptor.getValue().productId()).isEqualTo(30L);
        assertThat(requestCaptor.getValue().quantityDelta()).isEqualTo(-2);
        assertThat(requestCaptor.getValue().reason()).isEqualTo("REDUCCION_MASIVA_CSV");
    }

    @Test
    void shouldRejectInvalidHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "reduccion.csv",
                "text/csv",
                "barcode,cantidad\n7501234567890,2".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.importCsv(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("La plantilla no coincide. Usa las columnas codigo_producto,cantidad.");
    }
}
