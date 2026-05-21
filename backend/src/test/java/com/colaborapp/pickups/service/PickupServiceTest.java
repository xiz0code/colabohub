package com.colaborapp.pickups.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.pickups.domain.Pickup;
import com.colaborapp.pickups.domain.PickupStatus;
import com.colaborapp.pickups.repository.PickupRepository;
import com.colaborapp.pickups.web.dto.PickupResponse;
import com.colaborapp.pickups.web.dto.PreparePickupCheckoutResponse;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.repository.SaleRepository;
import com.colaborapp.sales.service.PosSaleService;
import com.colaborapp.sales.web.dto.PosManualSaleItemRequest;
import com.colaborapp.sales.web.dto.PosSaleResponse;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.AuthenticatedUserService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.User;

@ExtendWith(MockitoExtension.class)
class PickupServiceTest {

    @Mock
    private PickupRepository pickupRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private PosSaleService posSaleService;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private PickupService pickupService;

    private Tenant tenant;
    private Market market;
    private Store store;
    private Pickup pickup;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);

        market = new Market();
        market.setId(2L);
        market.setName("Espacio Test");

        store = new Store();
        store.setId(4L);
        store.setName("Stock principal");
        store.setMarket(market);

        pickup = new Pickup();
        pickup.setId(15L);
        pickup.setTenant(tenant);
        pickup.setMarket(market);
        pickup.setStore(store);
        pickup.setPickupNumber("BW2");
        pickup.setCustomerName("Cliente");
        pickup.setDescription("Retiro por cobrar");
        pickup.setPayable(true);
        pickup.setAmountDue(new BigDecimal("7900"));
        pickup.setStatus(PickupStatus.PENDING);
        pickup.setCollaboratorUserId(22L);
        pickup.setCollaboratorNameSnapshot("Butterfly");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(authenticatedUserService.getCurrentUserSnapshot()).thenReturn(currentUser());
    }

    @Test
    void shouldPreparePickupCheckoutFromGeneratedBarcode() {
        Sale linkedSale = new Sale();
        linkedSale.setId(70L);
        linkedSale.setStatus(SaleStatus.OPEN);

        when(pickupRepository.findByIdAndTenantIdWithDetails(15L, 1L)).thenReturn(Optional.of(pickup));
        when(posSaleService.getOpenSaleOrNull(2L)).thenReturn(posSale(70L));
        when(posSaleService.addManualItem(eq(70L), any(PosManualSaleItemRequest.class))).thenReturn(posSale(70L));
        when(saleRepository.findByIdAndTenantId(70L, 1L)).thenReturn(Optional.of(linkedSale));

        PreparePickupCheckoutResponse response = pickupService.prepareCheckoutByCode("RET-0000015");

        ArgumentCaptor<PosManualSaleItemRequest> requestCaptor = ArgumentCaptor.forClass(PosManualSaleItemRequest.class);
        verify(posSaleService).addManualItem(eq(70L), requestCaptor.capture());

        assertThat(response.pickup().pickupBarcode()).isEqualTo("RET-0000015");
        assertThat(response.pickup().storeName()).isEqualTo("Butterfly");
        assertThat(requestCaptor.getValue().reference()).isEqualTo("pickup:15");
        assertThat(requestCaptor.getValue().collaboratorUserId()).isEqualTo(22L);
        assertThat(requestCaptor.getValue().collaboratorName()).isEqualTo("Butterfly");
    }

    @Test
    void shouldFilterPickupListByCollaboratorForStoreUsers() {
        User storeUser = new User();
        storeUser.setId(22L);
        storeUser.setEmail("butterfly@example.com");
        storeUser.setFullName("Butterfly");

        when(authenticatedUserService.getCurrentUserSnapshot()).thenReturn(new AuthenticatedUserService.CurrentAuthenticatedUser(
                storeUser,
                List.of("STORE_USER"),
                true,
                2L,
                "Espacio Test",
                List.of(2L),
                List.of(4L),
                List.of("Espacio Test")));
        when(pickupRepository.search(1L, null, List.of(), true, 22L, null, null)).thenReturn(List.of(pickup));

        List<PickupResponse> responses = pickupService.list(null, null, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().storeName()).isEqualTo("Butterfly");
    }

    @Test
    void shouldRejectStoreUserWhenPickupBelongsToAnotherCollaborator() {
        User storeUser = new User();
        storeUser.setId(22L);
        storeUser.setEmail("butterfly@example.com");
        storeUser.setFullName("Butterfly");

        Pickup foreignPickup = new Pickup();
        foreignPickup.setId(18L);
        foreignPickup.setTenant(tenant);
        foreignPickup.setMarket(market);
        foreignPickup.setStore(store);
        foreignPickup.setPickupNumber("OTRO");
        foreignPickup.setCustomerName("Cliente");
        foreignPickup.setDescription("Retiro ajeno");
        foreignPickup.setPayable(true);
        foreignPickup.setAmountDue(new BigDecimal("5000"));
        foreignPickup.setStatus(PickupStatus.PENDING);
        foreignPickup.setCollaboratorUserId(55L);
        foreignPickup.setCollaboratorNameSnapshot("Otra tienda");

        when(authenticatedUserService.getCurrentUserSnapshot()).thenReturn(new AuthenticatedUserService.CurrentAuthenticatedUser(
                storeUser,
                List.of("STORE_USER"),
                true,
                2L,
                "Espacio Test",
                List.of(2L),
                List.of(4L),
                List.of("Espacio Test")));
        when(accessControlService.hasRole(com.colaborapp.users.domain.RoleCode.STORE_USER)).thenReturn(true);
        when(pickupRepository.findByIdAndTenantIdWithDetails(18L, 1L)).thenReturn(Optional.of(foreignPickup));

        assertThatThrownBy(() -> pickupService.getPickupForLabel(18L))
                .isInstanceOf(com.colaborapp.common.exception.BusinessException.class)
                .hasMessageContaining("No tienes acceso");
    }

    private AuthenticatedUserService.CurrentAuthenticatedUser currentUser() {
        User user = new User();
        user.setId(99L);
        user.setEmail("seller@example.com");
        user.setFullName("Seller");
        user.setActive(true);
        return new AuthenticatedUserService.CurrentAuthenticatedUser(
                user,
                List.of("ADMIN_MARKET"),
                true,
                2L,
                "Espacio Test",
                List.of(2L),
                List.of(),
                List.of("Espacio Test"));
    }

    private PosSaleResponse posSale(Long id) {
        return new PosSaleResponse(
                id,
                "S-2026-00000070",
                2L,
                SaleStatus.OPEN,
                PaymentMethod.CASH,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                Instant.parse("2026-05-11T12:00:00Z"),
                null,
                null,
                null,
                null,
                List.of(),
                List.of());
    }
}
