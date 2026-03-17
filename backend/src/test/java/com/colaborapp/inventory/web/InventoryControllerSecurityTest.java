package com.colaborapp.inventory.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.colaborapp.common.exception.GlobalExceptionHandler;
import com.colaborapp.config.SecurityConfig;
import com.colaborapp.inventory.service.InventoryService;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.GoogleOAuth2UserService;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
class InventoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean(name = "accessControl")
    private AccessControlService accessControlService;

    @MockBean
    private GoogleOAuth2UserService googleOAuth2UserService;

    @Test
    void storeUserCannotModifyStock() throws Exception {
        when(accessControlService.canManageInventory()).thenReturn(false);

        mockMvc.perform(post("/api/inventory/adjustments")
                        .with(user("colaborador@correo.cl"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":3,"quantityDelta":1,"reason":"MANUAL"}
                                """))
                .andExpect(status().isForbidden());
    }
}
