package com.colaborapp.markets.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import com.colaborapp.config.SecurityConfig;
import com.colaborapp.markets.service.MarketService;
import com.colaborapp.markets.web.dto.MarketResponse;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.GoogleOAuth2UserService;

@WebMvcTest(MarketController.class)
@AutoConfigureMockMvc
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
class MarketControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketService marketService;

    @MockBean(name = "accessControl")
    private AccessControlService accessControlService;

    @MockBean
    private GoogleOAuth2UserService googleOAuth2UserService;

    @Test
    void unauthenticatedCreateTiendaReturns401() throws Exception {
        mockMvc.perform(post("/api/markets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tienda Norte","email":"tienda@correo.cl"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminSystemCanCreateTienda() throws Exception {
        when(accessControlService.canManageMarkets()).thenReturn(true);
        when(marketService.createMarket(any())).thenReturn(new MarketResponse(
                1L,
                "Tienda Norte",
                "tienda@correo.cl",
                "+56911111111",
                "Ana Perez",
                "Descripcion",
                "Santiago",
                "CLP",
                false,
                true,
                Instant.now(),
                Instant.now()));

        mockMvc.perform(post("/api/markets")
                        .with(user("admin@correo.cl"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tienda Norte","email":"tienda@correo.cl","phone":"+56911111111","contactName":"Ana Perez","description":"Descripcion","active":true}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void adminMarketCannotCreateTienda() throws Exception {
        when(accessControlService.canManageMarkets()).thenReturn(false);

        mockMvc.perform(post("/api/markets")
                        .with(user("market@correo.cl"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tienda Norte","email":"tienda@correo.cl"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateEmailReturnsFriendlyValidationMessage() throws Exception {
        when(accessControlService.canManageMarkets()).thenReturn(true);
        when(marketService.createMarket(any()))
                .thenThrow(new BusinessException("Este correo ya está asociado a un usuario del sistema."));

        mockMvc.perform(post("/api/markets")
                        .with(user("admin@correo.cl"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tienda Norte","email":"tienda@correo.cl"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este correo ya está asociado a un usuario del sistema."));
    }
}
