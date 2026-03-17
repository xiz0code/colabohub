package com.colaborapp.closings.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.MockMvc;

import com.colaborapp.closings.service.DailyClosingService;
import com.colaborapp.closings.web.dto.DailyClosingResponse;
import com.colaborapp.closings.web.dto.DailyClosingStoreResponse;
import com.colaborapp.common.exception.GlobalExceptionHandler;
import com.colaborapp.common.exception.ResourceNotFoundException;

@WebMvcTest(DailyClosingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DailyClosingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DailyClosingService dailyClosingService;

    @Test
    void shouldCloseDailyForMarket() throws Exception {
        LocalDate date = LocalDate.of(2026, 3, 15);
        when(dailyClosingService.closeDay(eq(5L), eq(date), eq("system"))).thenReturn(response());

        mockMvc.perform(post("/api/markets/5/closings/daily")
                        .queryParam("date", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketId").value(5))
                .andExpect(jsonPath("$.stores[0].storeName").value("Tienda Ana"));
    }

    @Test
    void shouldReturnExistingClosingWhenAlreadyCreated() throws Exception {
        LocalDate date = LocalDate.of(2026, 3, 15);
        when(dailyClosingService.closeDay(eq(5L), eq(date), eq("system"))).thenReturn(response());

        mockMvc.perform(post("/api/markets/5/closings/daily")
                        .queryParam("date", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDate").value("2026-03-15"))
                .andExpect(jsonPath("$.saleCount").value(2));
    }

    @Test
    void shouldGetClosingByDate() throws Exception {
        when(dailyClosingService.getClosing(5L, LocalDate.of(2026, 3, 15))).thenReturn(response());

        mockMvc.perform(get("/api/markets/5/closings/2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(35000.0))
                .andExpect(jsonPath("$.stores[0].saleCount").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenClosingDoesNotExist() throws Exception {
        when(dailyClosingService.getClosing(5L, LocalDate.of(2026, 3, 15)))
                .thenThrow(new ResourceNotFoundException("Daily closing not found for market 5 and date 2026-03-15."));

        mockMvc.perform(get("/api/markets/5/closings/2026-03-15"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Daily closing not found for market 5 and date 2026-03-15."));
    }

    private DailyClosingResponse response() {
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
}
