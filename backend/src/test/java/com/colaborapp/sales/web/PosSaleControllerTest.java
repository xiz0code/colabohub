package com.colaborapp.sales.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.GlobalExceptionHandler;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.SaleItemPricingType;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.service.PosSaleService;
import com.colaborapp.sales.web.dto.PosSaleItemResponse;
import com.colaborapp.sales.web.dto.PosSaleResponse;
import com.colaborapp.sales.web.dto.PosSaleStoreSummaryResponse;
import com.colaborapp.sales.web.dto.PosSaleSummaryResponse;

@WebMvcTest(PosSaleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PosSaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PosSaleService posSaleService;

    @Test
    void shouldCreateSale() throws Exception {
        when(posSaleService.createSale(any())).thenReturn(response(1L, SaleStatus.OPEN));

        mockMvc.perform(post("/api/pos/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void shouldListSales() throws Exception {
        when(posSaleService.listSales()).thenReturn(List.of(summary(1L, SaleStatus.CONFIRMED)));

        mockMvc.perform(get("/api/pos/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].saleNumber").value("S-2026-00000001"))
                .andExpect(jsonPath("$[0].ivaAmount").value(1900.00));
    }

    @Test
    void shouldGetSale() throws Exception {
        when(posSaleService.getSale(1L)).thenReturn(response(1L, SaleStatus.OPEN));

        mockMvc.perform(get("/api/pos/sales/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleNumber").value("S-2026-00000001"));
    }

    @Test
    void shouldAddItem() throws Exception {
        when(posSaleService.addItem(eq(1L), any())).thenReturn(response(1L, SaleStatus.OPEN));

        mockMvc.perform(post("/api/pos/sales/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1000,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(1000));
    }

    @Test
    void shouldReturnValidationErrorWhenAddItemQuantityIsInvalid() throws Exception {
        mockMvc.perform(post("/api/pos/sales/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1000,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.quantity").exists());
    }

    @Test
    void shouldReturnBusinessErrorWhenSaleIsImmutable() throws Exception {
        when(posSaleService.addItem(eq(1L), any()))
                .thenThrow(new BusinessException("Only open sales can be modified. Current status: CONFIRMED."));

        mockMvc.perform(post("/api/pos/sales/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1000,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only open sales can be modified. Current status: CONFIRMED."));
    }

    @Test
    void shouldUpdateItem() throws Exception {
        when(posSaleService.updateItem(eq(1L), eq(500L), any())).thenReturn(response(1L, SaleStatus.OPEN));

        mockMvc.perform(patch("/api/pos/sales/1/items/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void shouldConfirmSale() throws Exception {
        when(posSaleService.confirm(1L)).thenReturn(response(1L, SaleStatus.CONFIRMED));

        mockMvc.perform(post("/api/pos/sales/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldCancelSaleWithReason() throws Exception {
        when(posSaleService.cancel(1L, "Cliente solicito anulacion")).thenReturn(response(1L, SaleStatus.CANCELLED));

        mockMvc.perform(post("/api/pos/sales/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cliente solicito anulacion\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldValidateCancelReason() throws Exception {
        mockMvc.perform(post("/api/pos/sales/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.reason").exists());
    }

    @Test
    void shouldGetOpenSale() throws Exception {
        when(posSaleService.getOpenSaleOrNull(null)).thenReturn(response(7L, SaleStatus.OPEN));

        mockMvc.perform(get("/api/pos/sales/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.totalAmount").value(12000.00));
    }

    private PosSaleSummaryResponse summary(Long id, SaleStatus status) {
        return new PosSaleSummaryResponse(
                id,
                "S-2026-00000001",
                "POS",
                Instant.parse("2026-03-15T12:00:00Z"),
                status,
                new BigDecimal("11900.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("1900.00"),
                new BigDecimal("11900.00"),
                PaymentMethod.CASH,
                1L,
                "admin.tienda@colabohub.cl");
    }

    private PosSaleResponse response(Long id, SaleStatus status) {
        return new PosSaleResponse(
                id,
                "S-2026-00000001",
                1L,
                status,
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
                status == SaleStatus.CONFIRMED ? Instant.parse("2026-03-15T12:05:00Z") : null,
                status == SaleStatus.CANCELLED ? Instant.parse("2026-03-15T12:10:00Z") : null,
                status == SaleStatus.CANCELLED ? "admin@colabohub.cl" : null,
                status == SaleStatus.CANCELLED ? "Cliente solicito anulacion" : null,
                List.of(new PosSaleItemResponse(
                        500L,
                        1000L,
                        false,
                        null,
                        10L,
                        "Tienda Ana",
                        "Aro Flor",
                        "Camila",
                        "ANA-001",
                        "7500000000101",
                        1,
                        9,
                        new BigDecimal("12000.00"),
                        new BigDecimal("12000.00"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("12000.00"),
                        SaleItemPricingType.NORMAL,
                        null,
                        null,
                        new BigDecimal("66.00"),
                        new BigDecimal("228.00"),
                        new BigDecimal("56.00"),
                        new BigDecimal("350.00"),
                        new BigDecimal("11650.00"),
                        false,
                        new BigDecimal("39000.00"),
                        new BigDecimal("0.00169"),
                        new BigDecimal("0.0079"),
                        new BigDecimal("11650.00"),
                        new BigDecimal("12000.00"))),
                List.of(new PosSaleStoreSummaryResponse(
                        10L,
                        "Tienda Ana",
                        1,
                        1,
                        new BigDecimal("12000.00"),
                        new BigDecimal("66.00"),
                        new BigDecimal("228.00"),
                        new BigDecimal("56.00"),
                        new BigDecimal("350.00"),
                        new BigDecimal("11650.00"))));
    }
}
