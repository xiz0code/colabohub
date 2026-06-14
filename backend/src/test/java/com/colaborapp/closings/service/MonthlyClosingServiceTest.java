package com.colaborapp.closings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.closings.domain.MonthlyClosing;
import com.colaborapp.closings.repository.MonthlyClosingCollaboratorRepository;
import com.colaborapp.closings.repository.MonthlyClosingRepository;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.tenant.domain.Tenant;

@ExtendWith(MockitoExtension.class)
class MonthlyClosingServiceTest {

    @Mock
    private MonthlyClosingRepository monthlyClosingRepository;

    @Mock
    private MonthlyClosingCollaboratorRepository monthlyClosingCollaboratorRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private CollaboratorSalesSummaryService collaboratorSalesSummaryService;

    @Mock
    private ClosingPaymentMethodSummaryService closingPaymentMethodSummaryService;

    @Mock
    private CollaboratorClosingEmailService collaboratorClosingEmailService;

    private MonthlyClosingService monthlyClosingService;
    private Tenant tenant;
    private Market market;

    @BeforeEach
    void setUp() {
        monthlyClosingService = new MonthlyClosingService(
                monthlyClosingRepository,
                monthlyClosingCollaboratorRepository,
                marketRepository,
                currentTenantProvider,
                collaboratorSalesSummaryService,
                closingPaymentMethodSummaryService,
                collaboratorClosingEmailService,
                "America/Santiago");
        lenient().when(closingPaymentMethodSummaryService.summarizeByMarketAndPeriod(any(), any(), any())).thenReturn(List.of());

        tenant = new Tenant();
        tenant.setId(1L);

        market = new Market();
        market.setId(5L);
        market.setName("Sakura Store");
    }

    @Test
    void closeMonthCreatesClosingAndCollaboratorRows() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(monthlyClosingRepository.findByMarketIdAndClosingMonthWithMarket(1L, 5L, LocalDate.of(2026, 3, 1)))
                .thenReturn(Optional.empty());
        when(marketRepository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(market));
        when(collaboratorSalesSummaryService.summarizeByMarketAndPeriod(any(), any(), any()))
                .thenReturn(List.of(new CollaboratorSalesSummaryService.CollaboratorSummary(
                        7L,
                        "Camila",
                        "camila@example.com",
                        false,
                        2L,
                        5L,
                        new BigDecimal("12000.0000"),
                        new BigDecimal("600.0000"),
                        new BigDecimal("11400.0000"),
                        new BigDecimal("1915.9664"),
                        new BigDecimal("1915.9664"),
                        List.of(new CollaboratorSalesSummaryService.CollaboratorProductSummary(
                                "Sticker BTS",
                                "STK-001",
                                5,
                                new BigDecimal("12000.0000"),
                                new BigDecimal("600.0000"),
                                new BigDecimal("11400.0000"))),
                        List.of())));
        when(monthlyClosingRepository.save(any(MonthlyClosing.class))).thenAnswer(invocation -> {
            MonthlyClosing closing = invocation.getArgument(0);
            closing.setId(70L);
            return closing;
        });

        var response = monthlyClosingService.closeMonth(5L, YearMonth.of(2026, 3), "tester");

        assertThat(response.marketName()).isEqualTo("Sakura Store");
        assertThat(response.collaborators()).hasSize(1);
        assertThat(response.totalSalesAmount()).isEqualByComparingTo("12000.0000");
        verify(monthlyClosingCollaboratorRepository).saveAll(any());
        verify(collaboratorClosingEmailService).sendMonthlySummary(any(), any(), any());
    }
}
