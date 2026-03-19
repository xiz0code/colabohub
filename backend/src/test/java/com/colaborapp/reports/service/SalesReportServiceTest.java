package com.colaborapp.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
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
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;

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
    }

    @Test
    void storeUserTodayDetailsOnlyIncludesAllowedStoreScope() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(false);
        when(accessControlService.hasRole(RoleCode.STORE_USER)).thenReturn(true);
        when(accessControlService.currentStoreIds()).thenReturn(List.of(10L));
        when(saleStoreSummaryRepository.findByPeriodWithSaleAndStore(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(SaleStatus.CONFIRMED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        summary(100L, "S-100", 10L, 1L, "Tienda A", "12000.0000", "500.0000", "11500.0000"),
                        summary(100L, "S-100", 11L, 1L, "Tienda B", "8000.0000", "300.0000", "7700.0000"),
                        summary(101L, "S-101", 11L, 1L, "Tienda B", "9000.0000", "350.0000", "8650.0000")));

        var response = salesReportService.getTodayDetails();

        assertThat(response.salesCount()).isEqualTo(1L);
        assertThat(response.stores()).hasSize(1);
        assertThat(response.sales()).hasSize(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("12000.0000");
        assertThat(response.sales().getFirst().totalAmount()).isEqualByComparingTo("12000.0000");
        assertThat(response.sales().getFirst().stores()).hasSize(1);
        assertThat(response.sales().getFirst().stores().getFirst().storeId()).isEqualTo(10L);
    }

    @Test
    void collaboratorDashboardSummaryUsesAllowedStoreScopeAndProductCounts() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(false);
        when(accessControlService.hasRole(RoleCode.STORE_USER)).thenReturn(true);
        when(accessControlService.currentStoreIds()).thenReturn(List.of(10L));
        when(saleStoreSummaryRepository.findByPeriodWithSaleAndStore(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(SaleStatus.CONFIRMED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        summary(200L, "S-200", 10L, 1L, "Tienda A", "15000.0000", "700.0000", "14300.0000"),
                        summary(201L, "S-201", 11L, 1L, "Tienda B", "21000.0000", "900.0000", "20100.0000")));
        when(productRepository.countByTenantIdAndStore_IdInAndStatus(1L, List.of(10L), ProductStatus.ACTIVE)).thenReturn(6L);
        when(productRepository.countByTenantIdAndStore_IdInAndStatusAndStockLessThanEqual(1L, List.of(10L), ProductStatus.ACTIVE, 5))
                .thenReturn(2L);

        var response = salesReportService.getDashboardSummary();

        assertThat(response.salesCount()).isEqualTo(1L);
        assertThat(response.totalAmount()).isEqualByComparingTo("15000.0000");
        assertThat(response.activeProducts()).isEqualTo(6L);
        assertThat(response.lowStockProducts()).isEqualTo(2L);
        assertThat(response.stores()).extracting(store -> store.storeId()).containsExactly(10L);
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

        Store store = new Store();
        store.setId(10L);
        store.setName("Tienda A");
        store.setMarket(market);
        store.setTenant(currentTenant);

        Sale sale = new Sale();
        sale.setId(500L);
        sale.setTenant(currentTenant);
        sale.setSaleNumber("S-500");
        sale.setStatus(SaleStatus.CONFIRMED);
        sale.setConfirmedAt(Instant.parse("2026-03-15T14:00:00Z"));

        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setStore(store);
        item.setCollaboratorUserId(7L);
        item.setCollaboratorNameSnapshot("Camila");
        item.setProductNameSnapshot(productName);
        item.setQuantity(quantity);
        item.setSubtotal(new BigDecimal(subtotal));
        item.setTotalCommissionAmount(new BigDecimal(commission));
        item.setNetAmount(new BigDecimal(net));
        return item;
    }
}
