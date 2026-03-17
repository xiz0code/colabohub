package com.colaborapp.closings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.closings.domain.DailyClosing;
import com.colaborapp.closings.domain.DailyClosingStore;
import com.colaborapp.closings.repository.DailyClosingRepository;
import com.colaborapp.closings.repository.DailyClosingStoreRepository;
import com.colaborapp.closings.web.dto.DailyClosingResponse;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.repository.SaleStoreSummaryRepository;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;

@ExtendWith(MockitoExtension.class)
class DailyClosingServiceTest {

    @Mock
    private DailyClosingRepository dailyClosingRepository;

    @Mock
    private DailyClosingStoreRepository dailyClosingStoreRepository;

    @Mock
    private SaleStoreSummaryRepository saleStoreSummaryRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private DailyClosingEmailService dailyClosingEmailService;

    private DailyClosingService dailyClosingService;

    private Tenant tenant;
    private Market market;
    private LocalDate closingDate;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);

        market = new Market();
        market.setId(5L);
        market.setName("Mercado Creativo");
        market.setEmail("admin@mercado.cl");

        closingDate = LocalDate.of(2026, 3, 15);

        dailyClosingService = new DailyClosingService(
                dailyClosingRepository,
                dailyClosingStoreRepository,
                saleStoreSummaryRepository,
                marketRepository,
                storeRepository,
                currentTenantProvider,
                dailyClosingEmailService,
                "America/Santiago");
    }

    @Test
    void closeDayCreatesOneClosingPerMarketDate() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(saleStoreSummaryRepository.summarizeClosingTotals(any(), any(), any(), any(), any()))
                .thenReturn(new Object[] {2L, new BigDecimal("35000.0000"), new BigDecimal("2800.0000"), new BigDecimal("32200.0000")});
        when(saleStoreSummaryRepository.summarizeClosingStores(any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        new Object[] {11L, "Tienda Ana", 1L, new BigDecimal("20000.0000"), new BigDecimal("1500.0000"), new BigDecimal("18500.0000"), 2L},
                        new Object[] {12L, "Tienda Lucia", 1L, new BigDecimal("15000.0000"), new BigDecimal("1300.0000"), new BigDecimal("13700.0000"), 1L}));
        when(storeRepository.getReferenceById(11L)).thenReturn(store(11L, "Tienda Ana"));
        when(storeRepository.getReferenceById(12L)).thenReturn(store(12L, "Tienda Lucia"));
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(90L);
            return closing;
        });

        DailyClosingResponse response = dailyClosingService.closeDay(5L, closingDate, "tester");

        ArgumentCaptor<DailyClosing> closingCaptor = ArgumentCaptor.forClass(DailyClosing.class);
        verify(dailyClosingRepository).save(closingCaptor.capture());
        assertThat(closingCaptor.getValue().getMarket()).isEqualTo(market);
        assertThat(closingCaptor.getValue().getClosingDate()).isEqualTo(closingDate);
        assertThat(response.saleCount()).isEqualTo(2L);
        assertThat(response.totalSalesAmount()).isEqualByComparingTo("35000.0000");
        assertThat(response.stores()).hasSize(2);
        verify(dailyClosingEmailService).sendClosingSummary("admin@mercado.cl", response);
        verify(saleStoreSummaryRepository).summarizeClosingTotals(eq(1L), eq(5L), eq(SaleStatus.CONFIRMED), any(), any());
    }

    @Test
    void closeDayReturnsExistingClosingIfAlreadyCreated() {
        DailyClosing existing = new DailyClosing();
        existing.setId(91L);
        existing.setMarket(market);
        existing.setClosingDate(closingDate);
        existing.setSaleCount(3L);
        existing.setTotalSalesAmount(new BigDecimal("50000.0000"));
        existing.setTotalCommissionAmount(new BigDecimal("4000.0000"));
        existing.setTotalNetAmount(new BigDecimal("46000.0000"));
        existing.setClosedAt(Instant.now());
        existing.setClosedBy("system");

        DailyClosingStore store = new DailyClosingStore();
        store.setId(1L);
        store.setDailyClosing(existing);
        store.setStore(store(11L, "Tienda Ana"));
        store.setStoreNameSnapshot("Tienda Ana");
        store.setSaleCount(2L);
        store.setTotalSalesAmount(new BigDecimal("50000.0000"));
        store.setTotalCommissionAmount(new BigDecimal("4000.0000"));
        store.setTotalNetAmount(new BigDecimal("46000.0000"));
        store.setTotalItems(3L);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.of(existing));
        when(dailyClosingStoreRepository.findByDailyClosingIdOrderByStoreNameSnapshotAsc(91L)).thenReturn(List.of(store));

        DailyClosingResponse response = dailyClosingService.closeDay(5L, closingDate, "tester");

        assertThat(response.saleCount()).isEqualTo(3L);
        assertThat(response.stores()).hasSize(1);
        verify(dailyClosingRepository, never()).save(any(DailyClosing.class));
        verify(dailyClosingEmailService, never()).sendClosingSummary(any(), any());
    }

    @Test
    void closeDayAggregatesStoreTotalsCorrectly() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(saleStoreSummaryRepository.summarizeClosingTotals(any(), any(), any(), any(), any()))
                .thenReturn(new Object[] {3L, new BigDecimal("47000.0000"), new BigDecimal("3100.0000"), new BigDecimal("43900.0000")});
        when(saleStoreSummaryRepository.summarizeClosingStores(any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        new Object[] {11L, "Tienda Ana", 2L, new BigDecimal("32000.0000"), new BigDecimal("2100.0000"), new BigDecimal("29900.0000"), 3L},
                        new Object[] {12L, "Tienda Maria", 1L, new BigDecimal("15000.0000"), new BigDecimal("1000.0000"), new BigDecimal("14000.0000"), 1L}));
        when(storeRepository.getReferenceById(11L)).thenReturn(store(11L, "Tienda Ana"));
        when(storeRepository.getReferenceById(12L)).thenReturn(store(12L, "Tienda Maria"));
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(92L);
            return closing;
        });

        DailyClosingResponse response = dailyClosingService.closeDay(5L, closingDate, "tester");

        assertThat(response.stores()).hasSize(2);
        assertThat(response.stores().getFirst().storeName()).isEqualTo("Tienda Ana");
        assertThat(response.stores().getFirst().saleCount()).isEqualTo(2L);
        assertThat(response.stores().getFirst().totalItems()).isEqualTo(3L);
        assertThat(response.stores().get(1).storeName()).isEqualTo("Tienda Maria");
        assertThat(response.stores().get(1).totalNetAmount()).isEqualByComparingTo("14000.0000");
    }

    @Test
    void closeDayOnlyIncludesConfirmedSales() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(saleStoreSummaryRepository.summarizeClosingTotals(any(), any(), any(), any(), any()))
                .thenReturn(new Object[] {1L, new BigDecimal("12000.0000"), new BigDecimal("900.0000"), new BigDecimal("11100.0000")});
        when(saleStoreSummaryRepository.summarizeClosingStores(any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[] {11L, "Tienda Ana", 1L, new BigDecimal("12000.0000"), new BigDecimal("900.0000"), new BigDecimal("11100.0000"), 1L}));
        when(storeRepository.getReferenceById(11L)).thenReturn(store(11L, "Tienda Ana"));
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(94L);
            return closing;
        });

        DailyClosingResponse response = dailyClosingService.closeDay(5L, closingDate, "tester");

        assertThat(response.saleCount()).isEqualTo(1L);
        verify(saleStoreSummaryRepository).summarizeClosingTotals(eq(1L), eq(5L), eq(SaleStatus.CONFIRMED), any(), any());
        verify(saleStoreSummaryRepository).summarizeClosingStores(eq(1L), eq(5L), eq(SaleStatus.CONFIRMED), any(), any());
    }

    @Test
    void closeDayAggregatesFromPersistedStoreSummariesWithoutRecalculatingPricing() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(saleStoreSummaryRepository.summarizeClosingTotals(any(), any(), any(), any(), any()))
                .thenReturn(new Object[] {2L, new BigDecimal("26000.0000"), new BigDecimal("1800.0000"), new BigDecimal("24200.0000")});
        when(saleStoreSummaryRepository.summarizeClosingStores(any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[] {11L, "Tienda Ana", 2L, new BigDecimal("26000.0000"), new BigDecimal("1800.0000"), new BigDecimal("24200.0000"), 2L}));
        when(storeRepository.getReferenceById(11L)).thenReturn(store(11L, "Tienda Ana"));
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(95L);
            return closing;
        });

        DailyClosingResponse response = dailyClosingService.closeDay(5L, closingDate, "tester");

        assertThat(response.totalCommissionAmount()).isEqualByComparingTo("1800.0000");
        verify(saleStoreSummaryRepository, times(1)).summarizeClosingTotals(any(), any(), any(), any(), any());
        verify(saleStoreSummaryRepository, times(1)).summarizeClosingStores(any(), any(), any(), any(), any());
    }

    @Test
    void closeDayPersistsDailyClosingStoreRows() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(saleStoreSummaryRepository.summarizeClosingTotals(any(), any(), any(), any(), any()))
                .thenReturn(new Object[] {2L, new BigDecimal("35000.0000"), new BigDecimal("2800.0000"), new BigDecimal("32200.0000")});
        when(saleStoreSummaryRepository.summarizeClosingStores(any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        new Object[] {11L, "Tienda Ana", 1L, new BigDecimal("20000.0000"), new BigDecimal("1500.0000"), new BigDecimal("18500.0000"), 2L},
                        new Object[] {12L, "Tienda Lucia", 1L, new BigDecimal("15000.0000"), new BigDecimal("1300.0000"), new BigDecimal("13700.0000"), 1L}));
        when(storeRepository.getReferenceById(11L)).thenReturn(store(11L, "Tienda Ana"));
        when(storeRepository.getReferenceById(12L)).thenReturn(store(12L, "Tienda Lucia"));
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(96L);
            return closing;
        });

        dailyClosingService.closeDay(5L, closingDate, "tester");

        ArgumentCaptor<List<DailyClosingStore>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyClosingStoreRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().getFirst().getStoreNameSnapshot()).isEqualTo("Tienda Ana");
    }

    @Test
    void closeDayDoesNotBreakIfEmailFails() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(saleStoreSummaryRepository.summarizeClosingTotals(any(), any(), any(), any(), any()))
                .thenReturn(new Object[] {0L, new BigDecimal("0.0000"), new BigDecimal("0.0000"), new BigDecimal("0.0000")});
        when(saleStoreSummaryRepository.summarizeClosingStores(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(93L);
            return closing;
        });
        doThrow(new RuntimeException("SMTP down")).when(dailyClosingEmailService).sendClosingSummary(any(), any());

        assertThatCode(() -> dailyClosingService.closeDay(5L, closingDate, "tester"))
                .doesNotThrowAnyException();
    }

    @Test
    void getClosingReturnsNotFoundWhenAbsent() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyClosingService.getClosing(5L, closingDate))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Todavía no existe un cierre diario para esa tienda en la fecha seleccionada.");
    }

    private Store store(Long id, String name) {
        Store store = new Store();
        store.setId(id);
        store.setName(name);
        return store;
    }
}
