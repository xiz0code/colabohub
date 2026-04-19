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
import com.colaborapp.closings.repository.DailyClosingCollaboratorRepository;
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
    private DailyClosingCollaboratorRepository dailyClosingCollaboratorRepository;

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

    @Mock
    private CollaboratorSalesSummaryService collaboratorSalesSummaryService;

    @Mock
    private CollaboratorClosingEmailService collaboratorClosingEmailService;

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
                dailyClosingCollaboratorRepository,
                dailyClosingStoreRepository,
                saleStoreSummaryRepository,
                marketRepository,
                storeRepository,
                currentTenantProvider,
                dailyClosingEmailService,
                collaboratorSalesSummaryService,
                collaboratorClosingEmailService,
                "America/Santiago");
    }

    @Test
    void closeDayCreatesOneClosingPerMarketDate() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of(
                collaboratorSummary(11L, "Tienda Ana", "ana@tienda.cl", 1L, 2L, "20000.0000", "1500.0000", "18500.0000"),
                collaboratorSummary(12L, "Tienda Lucia", "lucia@tienda.cl", 1L, 1L, "15000.0000", "1300.0000", "13700.0000")));
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
        when(dailyClosingCollaboratorRepository.findByDailyClosingIdOrderByCollaboratorNameSnapshotAsc(91L)).thenReturn(List.of());
        when(dailyClosingStoreRepository.findByDailyClosingIdOrderByStoreNameSnapshotAsc(91L)).thenReturn(List.of(store));
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of());

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
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of(
                collaboratorSummary(11L, "Tienda Ana", "ana@tienda.cl", 2L, 3L, "32000.0000", "2100.0000", "29900.0000"),
                collaboratorSummary(12L, "Tienda Maria", "maria@tienda.cl", 1L, 1L, "15000.0000", "1000.0000", "14000.0000")));
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
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of(
                collaboratorSummary(11L, "Tienda Ana", "ana@tienda.cl", 1L, 1L, "12000.0000", "900.0000", "11100.0000")));
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(94L);
            return closing;
        });

        DailyClosingResponse response = dailyClosingService.closeDay(5L, closingDate, "tester");

        assertThat(response.saleCount()).isEqualTo(1L);
    }

    @Test
    void closeDayAggregatesFromPersistedStoreSummariesWithoutRecalculatingPricing() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of(
                collaboratorSummary(11L, "Tienda Ana", "ana@tienda.cl", 2L, 2L, "26000.0000", "1800.0000", "24200.0000")));
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(95L);
            return closing;
        });

        DailyClosingResponse response = dailyClosingService.closeDay(5L, closingDate, "tester");

        assertThat(response.totalCommissionAmount()).isEqualByComparingTo("1800.0000");
        verify(collaboratorSalesSummaryService, times(1)).summarizeByMarketAndPeriod(any(), any(), any());
    }

    @Test
    void closeDayPersistsDailyClosingStoreRows() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of(
                collaboratorSummary(11L, "Tienda Ana", "ana@tienda.cl", 1L, 2L, "20000.0000", "1500.0000", "18500.0000"),
                collaboratorSummary(12L, "Tienda Lucia", "lucia@tienda.cl", 1L, 1L, "15000.0000", "1300.0000", "13700.0000")));
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(96L);
            return closing;
        });

        dailyClosingService.closeDay(5L, closingDate, "tester");

        ArgumentCaptor<List<com.colaborapp.closings.domain.DailyClosingCollaborator>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyClosingCollaboratorRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().getFirst().getCollaboratorNameSnapshot()).isEqualTo("Tienda Ana");
    }

    @Test
    void closeDayDoesNotBreakIfEmailFails() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of());
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
    void closeDayHandlesEmptyCollaboratorSummaryGracefully() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of());
        when(dailyClosingRepository.save(any(DailyClosing.class))).thenAnswer(invocation -> {
            DailyClosing closing = invocation.getArgument(0);
            closing.setId(97L);
            return closing;
        });

        DailyClosingResponse response = dailyClosingService.closeDay(5L, closingDate, "tester");

        assertThat(response.saleCount()).isEqualTo(0L);
        assertThat(response.totalSalesAmount()).isEqualByComparingTo("0.0000");
        assertThat(response.stores()).isEmpty();
        verify(dailyClosingCollaboratorRepository, never()).saveAll(any());
    }

    @Test
    void getClosingRebuildsLegacyDailyClosingUsingCollaboratorSummaries() {
        DailyClosing existing = new DailyClosing();
        existing.setId(98L);
        existing.setMarket(market);
        existing.setClosingDate(closingDate);
        existing.setSaleCount(0L);
        existing.setTotalSalesAmount(BigDecimal.ZERO.setScale(4));
        existing.setTotalCommissionAmount(BigDecimal.ZERO.setScale(4));
        existing.setTotalNetAmount(BigDecimal.ZERO.setScale(4));
        existing.setClosedAt(Instant.now());
        existing.setClosedBy("legacy");

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(1L, 5L, closingDate)).thenReturn(Optional.of(existing));
        when(dailyClosingCollaboratorRepository.findByDailyClosingIdOrderByCollaboratorNameSnapshotAsc(98L)).thenReturn(List.of());
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of(
                collaboratorSummary(12L, "PKMSTORE", "pkm@example.com", 4L, 33L, "202500.0000", "633.0000", "201867.0000")));

        DailyClosingResponse response = dailyClosingService.getClosing(5L, closingDate);

        assertThat(response.saleCount()).isEqualTo(4L);
        assertThat(response.totalSalesAmount()).isEqualByComparingTo("202500.0000");
        assertThat(response.stores()).hasSize(1);
        assertThat(response.stores().getFirst().storeName()).isEqualTo("PKMSTORE");
        verify(dailyClosingCollaboratorRepository).saveAll(any());
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

    private CollaboratorSalesSummaryService.CollaboratorSummary collaboratorSummary(
            Long collaboratorUserId,
            String collaboratorName,
            String collaboratorEmail,
            long saleCount,
            long totalItems,
            String totalSalesAmount,
            String totalCommissionAmount,
            String totalNetAmount) {
        return new CollaboratorSalesSummaryService.CollaboratorSummary(
                collaboratorUserId,
                collaboratorName,
                collaboratorEmail,
                false,
                saleCount,
                totalItems,
                new BigDecimal(totalSalesAmount),
                new BigDecimal(totalCommissionAmount),
                new BigDecimal(totalNetAmount),
                BigDecimal.ZERO.setScale(4),
                BigDecimal.ZERO.setScale(4),
                List.of());
    }
}
