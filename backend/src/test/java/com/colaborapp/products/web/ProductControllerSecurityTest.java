package com.colaborapp.products.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.colaborapp.common.exception.GlobalExceptionHandler;
import com.colaborapp.common.web.dto.PageResponse;
import com.colaborapp.config.SecurityConfig;
import com.colaborapp.inventory.service.InventoryService;
import com.colaborapp.products.service.BarcodeLabelPdfService;
import com.colaborapp.products.service.ProductImportService;
import com.colaborapp.products.service.ProductService;
import com.colaborapp.products.web.dto.ProductResponse;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.GoogleOAuth2UserService;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
class ProductControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private BarcodeLabelPdfService barcodeLabelPdfService;

    @MockBean
    private ProductImportService productImportService;

    @MockBean(name = "accessControl")
    private AccessControlService accessControlService;

    @MockBean
    private GoogleOAuth2UserService googleOAuth2UserService;

    @Test
    void sellerCanReadProducts() throws Exception {
        when(accessControlService.canReadInventory()).thenReturn(true);
        when(productService.listProducts(any())).thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        mockMvc.perform(get("/api/products")
                        .with(user("seller@colabohub.cl")))
                .andExpect(status().isOk());
    }

    @Test
    void sellerCannotCreateProducts() throws Exception {
        when(accessControlService.canManageOwnCatalog()).thenReturn(false);

        mockMvc.perform(post("/api/products")
                        .with(user("seller@colabohub.cl"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerUserId": 10,
                                  "name": "Photo card",
                                  "sku": "PHOTO-CARD",
                                  "salePrice": 2000,
                                  "initialStock": 10
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void storeUserCanCreateProducts() throws Exception {
        when(accessControlService.canManageOwnCatalog()).thenReturn(true);
        when(productService.createProduct(any())).thenReturn(new ProductResponse(
                1L,
                2L,
                "Stock principal",
                7L,
                "PKMSTORE",
                "Photo card",
                "PHOTO-CARD",
                null,
                java.math.BigDecimal.valueOf(2000),
                null,
                10,
                com.colaborapp.products.domain.ProductStatus.ACTIVE,
                "7500000000100",
                false,
                null,
                null,
                null));

        mockMvc.perform(post("/api/products")
                        .with(user("tienda@colabohub.cl"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Photo card",
                                  "sku": "PHOTO-CARD",
                                  "salePrice": 2000,
                                  "initialStock": 10
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void storeUserCannotUpdateProducts() throws Exception {
        when(accessControlService.canManageCatalog()).thenReturn(false);

        mockMvc.perform(put("/api/products/1")
                        .with(user("tienda@colabohub.cl"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerUserId": 7,
                                  "name": "Photo card",
                                  "sku": "PHOTO-CARD",
                                  "salePrice": 2200,
                                  "stock": 9
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerCannotReadProductAuditTrail() throws Exception {
        when(accessControlService.canReadInventoryDetails()).thenReturn(false);

        mockMvc.perform(get("/api/products/15/audit")
                        .with(user("seller@colabohub.cl")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerCannotReadProductStockMovements() throws Exception {
        when(accessControlService.canReadInventoryDetails()).thenReturn(false);

        mockMvc.perform(get("/api/products/15/stock-movements")
                        .with(user("seller@colabohub.cl")))
                .andExpect(status().isForbidden());
    }
}
