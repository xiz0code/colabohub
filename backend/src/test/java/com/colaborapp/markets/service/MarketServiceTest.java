package com.colaborapp.markets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.markets.web.dto.MarketRequest;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.service.MarketAdminProvisioningService;

@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private MarketAdminProvisioningService marketAdminProvisioningService;

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private MarketService marketService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);
    }

    @Test
    void adminSystemCanManageTiendas() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(marketRepository.existsByTenantIdAndNameIgnoreCase(1L, "Tienda Norte")).thenReturn(false);
        when(marketRepository.save(any(Market.class))).thenAnswer(invocation -> {
            Market market = invocation.getArgument(0);
            market.setId(10L);
            return market;
        });
        when(storeRepository.findByMarketIdAndType(10L, StoreType.STOCK)).thenReturn(Optional.empty());

        var response = marketService.createMarket(new MarketRequest(
                "Tienda Norte",
                "tienda.norte@correo.cl",
                "+56911111111",
                "Ana Perez",
                "Tienda principal",
                null,
                null,
                null,
                true));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Tienda Norte");
        assertThat(response.phone()).isEqualTo("+56911111111");
        assertThat(response.contactName()).isEqualTo("Ana Perez");
        assertThat(response.active()).isTrue();
        verify(storeRepository).save(argThat(store ->
                store.getMarket().getId().equals(10L)
                        && store.getType() == StoreType.STOCK
                        && "Stock principal".equals(store.getName())));
        verify(marketAdminProvisioningService).provisionForMarket(argThat(market -> market.getId().equals(10L)));
    }

    @Test
    void shouldNotCreateDuplicateStockStoreWhenAlreadyExists() {
        Store existingStockStore = new Store();
        existingStockStore.setId(50L);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(marketRepository.existsByTenantIdAndNameIgnoreCase(1L, "Tienda Norte")).thenReturn(false);
        when(marketRepository.save(any(Market.class))).thenAnswer(invocation -> {
            Market market = invocation.getArgument(0);
            market.setId(10L);
            return market;
        });
        when(storeRepository.findByMarketIdAndType(10L, StoreType.STOCK)).thenReturn(Optional.of(existingStockStore));

        marketService.createMarket(new MarketRequest(
                "Tienda Norte",
                "tienda.norte@correo.cl",
                null,
                null,
                null,
                null,
                null,
                null,
                true));

        verify(storeRepository, never()).save(any(Store.class));
    }

    @Test
    void adminMarketCannotCreateTiendas() {
        doThrow(new AccessDeniedException("You do not have permission to perform this action."))
                .when(accessControlService)
                .requireAnyRole(RoleCode.ADMIN_SYSTEM);

        assertThatThrownBy(() -> marketService.createMarket(new MarketRequest(
                "Tienda Sur",
                "tienda.sur@correo.cl",
                null,
                null,
                null,
                null,
                null,
                null,
                true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission to perform this action.");
    }

    @Test
    void nonSystemUsersOnlySeeAssignedTiendas() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(false);
        when(accessControlService.currentMarketIds()).thenReturn(List.of(5L));

        Market market = new Market();
        market.setId(5L);
        market.setName("Tienda Centro");
        market.setEmail("tienda@correo.cl");
        market.setActive(true);

        when(marketRepository.findByTenantIdAndIdInOrderByNameAsc(1L, List.of(5L))).thenReturn(List.of(market));

        assertThat(marketService.listMarkets()).hasSize(1);
        assertThat(marketService.listMarkets().getFirst().id()).isEqualTo(5L);
    }
}
