package com.colaborapp.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.pickups.repository.PickupRepository;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleItem;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.domain.SaleStoreSummary;
import com.colaborapp.sales.repository.SaleItemRepository;
import com.colaborapp.sales.repository.SaleRepository;
import com.colaborapp.sales.repository.SaleStoreSummaryRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;
import com.colaborapp.security.AuthenticatedUserService;

@ExtendWith(MockitoExtension.class)
class SalesReportServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleStoreSummaryRepository saleStoreSummaryRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PickupRepository pickupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private AccessControlService accessControlService;

    private SalesReportService salesReportService;
    private Tenant tenant;
    private User collaborator;

    @BeforeEach
    void setUp() {
        salesReportService = new SalesReportService(
                saleRepository,
                saleItemRepository,
                saleStoreSummaryRepository,
                storeRepository,
                marketRepository,
                productRepository,
                pickupRepository,
                userRepository,
                currentTenantProvider,
                accessControlService,
                "America/Santiago");

        tenant = new Tenant();
        tenant.setId(1L);

        collaborator = new User();
        collaborator.setId(7L);
        collaborator.setFullName("Camila");
        Market collaboratorMarket = new Market();
        collaboratorMarket.setId(1L);
        collaborator.getMarkets().add(collaboratorMarket);
        Role collaboratorRole = new Role();
        collaboratorRole.setCode(RoleCode.STORE_USER);
        collaborator.getRoles().add(collaboratorRole);
        lenient().when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(collaborator));
        lenient().when(accessControlService.currentStoreIds()).thenReturn(List.of(1L));
        lenient().when(accessControlService.currentMarketIds()).thenReturn(List.of(1L));
        lenient().when(pickupRepository.countDashboardPending(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.nullable(Long.class),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(0L);
    }

    @Test
    void storeUserTodayDetailsOnlyIncludesAllowedStoreScope() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.STORE_USER)).thenReturn(true);
        when(accessControlService.getCurrentUser()).thenReturn(new AuthenticatedUserService.CurrentAuthenticatedUser(
                collaborator,
                List.of("STORE_USER"),
                true,
                1L,
                "Mercado 1",
                List.of(1L),
                Collections.emptyList(),
                List.of("Mercado 1")));
        when(saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(SaleStatus.CONFIRMED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        item("Sticker BTS", 2, "4000.0000", "200.0000", "3800.0000"),
                        item("Album", 1, "12000.0000", "500.0000", "11500.0000")));

        var response = salesReportService.getTodayDetails();

        assertThat(response.salesCount()).isEqualTo(1L);
        assertThat(response.stores()).hasSize(1);
        assertThat(response.sales()).hasSize(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("16000.0000");
        assertThat(response.stores().getFirst().storeName()).isEqualTo("Mercado 1");
    }

    @Test
    void collaboratorDashboardSummaryUsesAllowedStoreScopeAndProductCounts() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.STORE_USER)).thenReturn(true);
        when(accessControlService.getCurrentUser()).thenReturn(new AuthenticatedUserService.CurrentAuthenticatedUser(
                collaborator,
                List.of("STORE_USER"),
                true,
                1L,
                "Mercado 1",
                List.of(1L),
                Collections.emptyList(),
                List.of("Mercado 1")));
        when(saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(SaleStatus.CONFIRMED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        item("Sticker BTS", 2, "4000.0000", "200.0000", "3800.0000"),
                        item("Album", 1, "12000.0000", "500.0000", "11500.0000")));
        when(productRepository.countByTenantIdAndOwnerUser_IdAndStatus(1L, 7L, ProductStatus.ACTIVE)).thenReturn(6L);
        when(productRepository.countByTenantIdAndOwnerUser_IdAndStatusAndStockLessThanEqual(1L, 7L, ProductStatus.ACTIVE, 5))
                .thenReturn(2L);
        when(pickupRepository.countDashboardPending(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(List.of(1L)),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(3L);

        var response = salesReportService.getDashboardSummary();

        assertThat(response.salesCount()).isEqualTo(1L);
        assertThat(response.totalAmount()).isEqualByComparingTo("16000.0000");
        assertThat(response.activeProducts()).isEqualTo(6L);
        assertThat(response.lowStockProducts()).isEqualTo(2L);
        assertThat(response.pendingPickups()).isEqualTo(3L);
        assertThat(response.trend()).hasSize(7);
        assertThat(response.stores()).extracting(store -> store.storeName()).containsExactly("Mercado 1");
    }

    @Test
    void storeUserTodayDetailsStillWorkWhenAssignedStoreIdsAreEmpty() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.STORE_USER)).thenReturn(true);
        when(accessControlService.currentStoreIds()).thenReturn(List.of());
        when(accessControlService.getCurrentUser()).thenReturn(new AuthenticatedUserService.CurrentAuthenticatedUser(
                collaborator,
                List.of("STORE_USER"),
                true,
                1L,
                "Mercado 1",
                List.of(1L),
                List.of(),
                List.of("Mercado 1")));
        when(saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(SaleStatus.CONFIRMED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(item("Sticker BTS", 2, "4000.0000", "200.0000", "3800.0000")));

        var response = salesReportService.getTodayDetails();

        assertThat(response.salesCount()).isEqualTo(1L);
        assertThat(response.totalAmount()).isEqualByComparingTo("4000.0000");
        assertThat(response.sales()).hasSize(1);
    }

    @Test
    void collaboratorSalesReportReturnsEntriesAndTotals() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(true);
        when(userRepository.findWithAccessById(7L)).thenReturn(java.util.Optional.of(collaborator));
        when(saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(SaleStatus.CONFIRMED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(item("Sticker BTS", 2, "4000.0000", "200.0000", "3800.0000")));

        var response = salesReportService.getCollaboratorSalesReport(
                7L,
                java.time.LocalDate.of(2026, 3, 15),
                java.time.LocalDate.of(2026, 3, 15));

        assertThat(response.collaboratorName()).isEqualTo("Camila");
        assertThat(response.entries()).hasSize(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("4000.0000");
        assertThat(response.totalCommissionAmount()).isEqualByComparingTo("200.0000");
        assertThat(response.totalNetAmount()).isEqualByComparingTo("3800.0000");
        assertThat(response.entries().getFirst().paymentMethod()).isEqualTo("DEBITO");
        assertThat(response.entries().getFirst().promotionLabel()).isEqualTo("2x1500");
        assertThat(response.entries().getFirst().fixedCommissionAmount()).isEqualByComparingTo("33.0000");
    }

    @Test
    void todayDetailsExcludeInactiveCollaborators() {
        User inactiveCollaborator = new User();
        inactiveCollaborator.setId(8L);
        inactiveCollaborator.setFullName("Tienda Inactiva");
        inactiveCollaborator.setActive(false);
        Role collaboratorRole = new Role();
        collaboratorRole.setCode(RoleCode.STORE_USER);
        inactiveCollaborator.getRoles().add(collaboratorRole);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.STORE_USER)).thenReturn(false);
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(true);
        when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(collaborator, inactiveCollaborator));
        when(saleItemRepository.findAllByTenantAndPeriodWithDetails(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(SaleStatus.CONFIRMED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        item("Sticker BTS", 2, "4000.0000", "200.0000", "3800.0000"),
                        itemForCollaborator(8L, "Tienda Inactiva", "Album oculto", 1, "12000.0000", "500.0000", "11500.0000")));

        var response = salesReportService.getTodayDetails();

        assertThat(response.salesCount()).isEqualTo(1L);
        assertThat(response.totalAmount()).isEqualByComparingTo("4000.0000");
        assertThat(response.sales()).singleElement().satisfies(sale -> {
            assertThat(sale.items()).extracting(itemResponse -> itemResponse.collaboratorName()).containsExactly("Camila");
            assertThat(sale.items()).extracting(itemResponse -> itemResponse.productName()).doesNotContain("Album oculto");
        });
    }

    private SaleStoreSummary summary(
            Long saleId,
            String saleNumber,
            Long storeId,
            Long marketId,
            String storeName,
            String subtotal,
            String commission,
            String net) {
        Tenant currentTenant = tenant;

        Market market = new Market();
        market.setId(marketId);
        market.setTenant(currentTenant);

        Store store = new Store();
        store.setId(storeId);
        store.setName(storeName);
        store.setMarket(market);
        store.setTenant(currentTenant);
        store.setStatus(StoreStatus.ACTIVE);

        Sale sale = new Sale();
        sale.setId(saleId);
        sale.setTenant(currentTenant);
        sale.setSaleNumber(saleNumber);
        sale.setStatus(SaleStatus.CONFIRMED);
        sale.setConfirmedAt(Instant.parse("2026-03-15T15:00:00Z"));

        SaleStoreSummary summary = new SaleStoreSummary();
        summary.setSale(sale);
        summary.setStore(store);
        summary.setLineCount(1);
        summary.setUnitCount(1);
        summary.setSubtotalAmount(new BigDecimal(subtotal));
        summary.setCommission1Amount(BigDecimal.ZERO.setScale(0));
        summary.setCommission2Amount(BigDecimal.ZERO.setScale(0));
        summary.setCommissionIvaAmount(BigDecimal.ZERO.setScale(0));
        summary.setTotalCommissionAmount(new BigDecimal(commission));
        summary.setNetAmount(new BigDecimal(net));
        return summary;
    }

    private SaleItem item(String productName, int quantity, String subtotal, String commission, String net) {
        Tenant currentTenant = tenant;

        Market market = new Market();
        market.setId(1L);
        market.setTenant(currentTenant);
        market.setName("Mercado 1");

        Store store = new Store();
        store.setId(1L);
        store.setName("Tienda A");
        store.setMarket(market);
        store.setTenant(currentTenant);
        store.setStatus(StoreStatus.ACTIVE);

        Sale sale = new Sale();
        sale.setId(500L);
        sale.setTenant(currentTenant);
        sale.setSaleNumber("S-500");
        sale.setStatus(SaleStatus.CONFIRMED);
        sale.setConfirmedAt(Instant.parse("2026-03-15T14:00:00Z"));
        sale.setUfValue(new BigDecimal("39000.0000"));
        sale.setPaymentMethod(com.colaborapp.sales.domain.PaymentMethod.DEBITO);

        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setStore(store);
        item.setCollaboratorUserId(7L);
        item.setCollaboratorNameSnapshot("Camila");
        item.setProductNameSnapshot(productName);
        item.setBaseUnitPrice(new BigDecimal("2000.0000"));
        item.setPricingType(com.colaborapp.sales.domain.SaleItemPricingType.PROMOTION);
        item.setAppliedPromotionName("2x1500");
        item.setQuantity(quantity);
        item.setCommission1Amount(new BigDecimal("33.0000"));
        item.setCommission2Amount(new BigDecimal("120.0000"));
        item.setCommissionIvaAmount(new BigDecimal("47.0000"));
        item.setSubtotal(new BigDecimal(subtotal));
        item.setTotalCommissionAmount(new BigDecimal(commission));
        item.setNetAmount(new BigDecimal(net));
        return item;
    }

    private SaleItem itemForCollaborator(
            Long collaboratorId,
            String collaboratorName,
            String productName,
            int quantity,
            String subtotal,
            String commission,
            String net) {
        SaleItem item = item(productName, quantity, subtotal, commission, net);
        item.setCollaboratorUserId(collaboratorId);
        item.setCollaboratorNameSnapshot(collaboratorName);
        return item;
    }
}
