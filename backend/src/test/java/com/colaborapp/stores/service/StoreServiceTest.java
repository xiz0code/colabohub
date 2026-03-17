package com.colaborapp.stores.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.web.dto.PageResponse;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.service.MarketService;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.stores.web.dto.StoreListQuery;
import com.colaborapp.stores.web.dto.StoreRequest;
import com.colaborapp.stores.web.dto.StoreResponse;
import com.colaborapp.tenant.domain.Tenant;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private MarketService marketService;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private StoreService storeService;

    private Tenant tenant;
    private Market market;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);

        market = new Market();
        market.setId(7L);
        market.setName("Mercado Norte");
    }

    @Test
    void shouldCreateStoreWhenCodeIsUnique() {
        StoreRequest request = new StoreRequest(7L, "COL-01", "Colaboradora Norte", StoreType.COLLABORATOR);
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(marketService.getMarketEntity(7L, tenant.getId())).thenReturn(market);
        when(storeRepository.existsByTenantIdAndCodeIgnoreCase(tenant.getId(), "COL-01")).thenReturn(false);
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.setId(10L);
            return store;
        });

        StoreResponse response = storeService.createStore(request);

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StoreStatus.ACTIVE);
        assertThat(captor.getValue().getMarket()).isEqualTo(market);
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.code()).isEqualTo("COL-01");
    }

    @Test
    void shouldRejectDuplicateStoreCode() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(marketService.getMarketEntity(7L, tenant.getId())).thenReturn(market);
        when(storeRepository.existsByTenantIdAndCodeIgnoreCase(tenant.getId(), "COL-01")).thenReturn(true);

        assertThatThrownBy(() -> storeService.createStore(new StoreRequest(7L, "COL-01", "Duplicada", StoreType.COLLABORATOR)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Store code already exists for the current tenant.");
    }

    @Test
    void shouldReturnPagedStores() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(true);
        Store store = new Store();
        store.setId(5L);
        store.setMarket(market);
        store.setCode("MAIN");
        store.setName("Principal");
        store.setType(StoreType.PRIMARY);
        store.setStatus(StoreStatus.ACTIVE);
        when(storeRepository.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(store)));

        PageResponse<StoreResponse> response = storeService.listStores(new StoreListQuery("pri", StoreStatus.ACTIVE, 0, 10));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().name()).isEqualTo("Principal");
    }
}
