package com.colaborapp.security.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import com.colaborapp.closings.service.DailyClosingService;
import com.colaborapp.closings.web.DailyClosingController;
import com.colaborapp.closings.web.dto.DailyClosingResponse;
import com.colaborapp.closings.web.dto.DailyClosingStoreResponse;
import com.colaborapp.common.exception.GlobalExceptionHandler;
import com.colaborapp.config.SecurityConfig;
import com.colaborapp.reports.service.SalesReportService;
import com.colaborapp.reports.web.MarketReportController;
import com.colaborapp.reports.web.SalesReportController;
import com.colaborapp.reports.web.dto.DashboardSummaryResponse;
import com.colaborapp.reports.web.dto.MarketSalesTodayReportResponse;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.SaleItemPricingType;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.service.PosSaleService;
import com.colaborapp.sales.web.PosSaleController;
import com.colaborapp.sales.web.dto.PosSaleItemResponse;
import com.colaborapp.sales.web.dto.PosSaleResponse;
import com.colaborapp.sales.web.dto.PosSaleStoreSummaryResponse;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.GoogleOAuth2UserService;

@WebMvcTest({ PosSaleController.class, DailyClosingController.class, MarketReportController.class, SalesReportController.class })
@AutoConfigureMockMvc
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
class ProtectedEndpointsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PosSaleService posSaleService;

    @MockBean
    private DailyClosingService dailyClosingService;

    @MockBean
    private SalesReportService salesReportService;

    @MockBean(name = "accessControl")
    private AccessControlService accessControlService;

    @MockBean
    private GoogleOAuth2UserService googleOAuth2UserService;

    @Test
    void unauthenticatedPosRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/pos/sales/open"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storeUserCanAccessScopedSalesTodayReadOnlyEndpoints() throws Exception {
        when(accessControlService.canAccessOperationalReports()).thenReturn(true);
        when(salesReportService.getTodayReport()).thenReturn(
                new com.colaborapp.reports.web.dto.SalesTodayReportResponse(
                        LocalDate.of(2026, 3, 15),
                        new BigDecimal("12000.00"),
                        new BigDecimal("12000.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("11500.00"),
                        1L,
                        List.of()));
        when(salesReportService.getTodayDetails()).thenReturn(
                new com.colaborapp.reports.web.dto.SalesTodayDetailsResponse(
                        LocalDate.of(2026, 3, 15),
                        new BigDecimal("12000.00"),
                        new BigDecimal("12000.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("11500.00"),
                        1L,
                        List.of(),
                        List.of()));

        mockMvc.perform(get("/api/reports/sales/today")
                        .with(user("colaborador@colaborapp.cl")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports/sales/today/details")
                        .with(user("colaborador@colaborapp.cl")))
                .andExpect(status().isOk());
    }

    @Test
    void adminSystemCanAccessGeneralDashboard() throws Exception {
        when(accessControlService.canAccessOperationalReports()).thenReturn(true);
        when(salesReportService.getDashboardSummary()).thenReturn(
                new DashboardSummaryResponse(
                        LocalDate.of(2026, 3, 15),
                        2L,
                        new BigDecimal("32000.00"),
                        new BigDecimal("1200.00"),
                        new BigDecimal("30800.00"),
                        12L,
                        3L,
                        List.of()));

        mockMvc.perform(get("/api/reports/sales/dashboard")
                        .with(user("admin@colaborapp.cl")))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserWithValidMarketAccessCanCloseDay() throws Exception {
        when(accessControlService.canManageClosings(5L)).thenReturn(true);
        when(dailyClosingService.closeDay(any(), any(), any())).thenReturn(closingResponse());

        mockMvc.perform(post("/api/markets/5/closings/daily")
                        .with(user("admin@colaborapp.cl"))
                        .with(csrf())
                        .queryParam("date", "2026-03-15"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserWithoutMarketAccessGets403OnClosing() throws Exception {
        when(accessControlService.canManageClosings(5L)).thenReturn(false);
        when(dailyClosingService.closeDay(any(), any(), any()))
                .thenThrow(new AccessDeniedException("You do not have permission to perform this action."));

        mockMvc.perform(post("/api/markets/5/closings/daily")
                        .with(user("user@colaborapp.cl"))
                        .with(csrf())
                        .queryParam("date", "2026-03-15"))
                .andExpect(status().isForbidden());
    }

    @Test
    void marketMismatchReturns403OnMarketReport() throws Exception {
        when(accessControlService.canAccessMarket(5L)).thenReturn(false);
        when(salesReportService.getMarketTodayReport(5L))
                .thenThrow(new AccessDeniedException("You do not have permission to perform this action."));

        mockMvc.perform(get("/api/reports/markets/5/sales/today")
                        .with(user("user@colaborapp.cl")))
                .andExpect(status().isForbidden());
    }

    @Test
    void saleOperationReturns403WhenPersistedSaleMarketIsForbidden() throws Exception {
        when(accessControlService.canOperatePos()).thenReturn(true);
        when(posSaleService.confirm(1L))
                .thenThrow(new AccessDeniedException("You do not have permission to perform this action."));

        mockMvc.perform(post("/api/pos/sales/1/confirm")
                        .with(user("user@colaborapp.cl")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserWithValidMarketAccessCanGetReport() throws Exception {
        when(accessControlService.canAccessMarket(5L)).thenReturn(true);
        when(salesReportService.getMarketTodayReport(5L)).thenReturn(
                new MarketSalesTodayReportResponse(
                        5L,
                        "Mercado",
                        LocalDate.of(2026, 3, 15),
                        new BigDecimal("1000.00"),
                        1L,
                        List.of()));

        mockMvc.perform(get("/api/reports/markets/5/sales/today")
                        .with(user("admin@colaborapp.cl")))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserWithValidPosAccessCanConfirmSale() throws Exception {
        when(accessControlService.canOperatePos()).thenReturn(true);
        when(posSaleService.confirm(1L)).thenReturn(posSaleResponse());

        mockMvc.perform(post("/api/pos/sales/1/confirm")
                        .with(user("collab@colaborapp.cl"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void storeUserCannotConfirmSale() throws Exception {
        when(accessControlService.canOperatePos()).thenReturn(false);

        mockMvc.perform(post("/api/pos/sales/1/confirm")
                        .with(user("colaborador@colaborapp.cl"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private DailyClosingResponse closingResponse() {
        return new DailyClosingResponse(
                5L,
                "Mercado Creativo",
                LocalDate.of(2026, 3, 15),
                2L,
                new BigDecimal("35000.0000"),
                new BigDecimal("2800.0000"),
                new BigDecimal("32200.0000"),
                Instant.parse("2026-03-15T23:00:00Z"),
                "system",
                List.of(new DailyClosingStoreResponse(
                        10L,
                        "Tienda Ana",
                        1L,
                        new BigDecimal("20000.0000"),
                        new BigDecimal("1500.0000"),
                        new BigDecimal("18500.0000"),
                        2L)));
    }

    private PosSaleResponse posSaleResponse() {
        return new PosSaleResponse(
                1L,
                "S-2026-00000001",
                5L,
                SaleStatus.CONFIRMED,
                PaymentMethod.CASH,
                new BigDecimal("10084.03"),
                new BigDecimal("1915.97"),
                new BigDecimal("12000.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("12000.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("12000.00"),
                new BigDecimal("39000.00"),
                new BigDecimal("0.00169"),
                new BigDecimal("0.0079"),
                Instant.parse("2026-03-15T12:00:00Z"),
                Instant.parse("2026-03-15T12:05:00Z"),
                null,
                null,
                null,
                List.of(new PosSaleItemResponse(
                        500L,
                        1000L,
                        10L,
                        "Tienda Ana",
                        "Aro Flor",
                        "Camila",
                        "ANA-001",
                        "7500000000101",
                        1,
                        new BigDecimal("12000.00"),
                        new BigDecimal("12000.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("12000.00"),
                        SaleItemPricingType.NORMAL,
                        null,
                        null,
                        BigDecimal.ZERO.setScale(0),
                        BigDecimal.ZERO.setScale(0),
                        BigDecimal.ZERO.setScale(0),
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("12000.00"),
                        false,
                        new BigDecimal("39000.00"),
                        new BigDecimal("0.00169"),
                        new BigDecimal("0.0079"),
                        new BigDecimal("12000.00"),
                        new BigDecimal("12000.00"))),
                List.of(new PosSaleStoreSummaryResponse(
                        10L,
                        "Tienda Ana",
                        1,
                        1,
                        new BigDecimal("12000.00"),
                        BigDecimal.ZERO.setScale(0),
                        BigDecimal.ZERO.setScale(0),
                        BigDecimal.ZERO.setScale(0),
                        BigDecimal.ZERO.setScale(0),
                        new BigDecimal("12000.00"))));
    }
}
