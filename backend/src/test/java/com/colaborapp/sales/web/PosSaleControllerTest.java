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
                .andExpect(jsonPath("$.error").value("Campos inválidos"))
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
    void shouldCancelSale() throws Exception {
        when(posSaleService.cancel(1L)).thenReturn(response(1L, SaleStatus.CANCELLED));

        mockMvc.perform(post("/api/pos/sales/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void shouldGetOpenSale() throws Exception {
        when(posSaleService.getOpenSaleOrNull(null)).thenReturn(response(7L, SaleStatus.OPEN));

        mockMvc.perform(get("/api/pos/sales/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    private PosSaleResponse response(Long id, SaleStatus status) {
        return new PosSaleResponse(
                id,
                "S-2026-00000001",
                status,
                PaymentMethod.CASH,
                new BigDecimal("12000.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("12000.00"),
                BigDecimal.ZERO.setScale(0),
                new BigDecimal("12000.00"),
                null,
                new BigDecimal("0.00169"),
                new BigDecimal("0.0079"),
                Instant.parse("2026-03-15T12:00:00Z"),
                status == SaleStatus.CONFIRMED ? Instant.parse("2026-03-15T12:05:00Z") : null,
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
