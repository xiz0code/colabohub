package com.colaborapp.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.inventory.domain.StockMovement;
import com.colaborapp.inventory.repository.StockMovementRepository;
import com.colaborapp.inventory.web.dto.StockAdjustmentRequest;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.tenant.domain.Tenant;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private InventoryService inventoryService;

    private Tenant tenant;
    private Product product;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);

        Store store = new Store();
        store.setId(2L);
        store.setName("Store");

        product = new Product();
        product.setId(3L);
        product.setTenant(tenant);
        product.setStore(store);
        product.setStock(4);
    }

    @Test
    void shouldPreventNegativeStockAdjustment() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(productRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> inventoryService.adjustStock(new StockAdjustmentRequest(3L, -5, "AJUSTE")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The requested stock adjustment would make stock negative.");
    }

    @Test
    void shouldPreventZeroStockAdjustment() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(productRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> inventoryService.adjustStock(new StockAdjustmentRequest(3L, 0, "AJUSTE")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Stock adjustment quantity must be different from zero.");
    }

    @Test
    void shouldRegisterInitialStockMovement() {
        inventoryService.registerInitialStock(product, 8);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousStock()).isZero();
        assertThat(captor.getValue().getNewStock()).isEqualTo(8);
        assertThat(captor.getValue().getReferenceType()).isEqualTo("INITIAL_STOCK");
    }

    @Test
    void shouldAdjustStockAndPersistMovement() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(productRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(product));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = inventoryService.adjustStock(new StockAdjustmentRequest(3L, 2, "MANUAL_ADJUSTMENT"));

        verify(productRepository).save(product);
        assertThat(product.getStock()).isEqualTo(6);
        assertThat(response.newStock()).isEqualTo(6);
        assertThat(response.quantity()).isEqualTo(2);
    }

    @Test
    void storeUserCannotAdjustStock() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("You do not have permission to perform this action."))
                .when(accessControlService)
                .requireAnyRole(
                        com.colaborapp.users.domain.RoleCode.ADMIN_SYSTEM,
                        com.colaborapp.users.domain.RoleCode.ADMIN_MARKET,
                        com.colaborapp.users.domain.RoleCode.COLLABORATOR);

        assertThatThrownBy(() -> inventoryService.adjustStock(new StockAdjustmentRequest(3L, 1, "AJUSTE")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission to perform this action.");
    }
}
