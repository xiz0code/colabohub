package com.colaborapp.security.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.colaborapp.security.AuthenticatedUserService;
import com.colaborapp.config.SecurityConfig;
import com.colaborapp.security.GoogleOAuth2UserService;
import com.colaborapp.users.domain.User;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticatedUserService authenticatedUserService;

    @MockBean
    private GoogleOAuth2UserService googleOAuth2UserService;

    @Test
    void shouldReturnAuthenticatedContext() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("xizocode@gmail.com");
        user.setFullName("Xizo Code");

        when(authenticatedUserService.getCurrentUserSnapshot()).thenReturn(
                new AuthenticatedUserService.CurrentAuthenticatedUser(
                        user,
                        List.of("ADMIN_SYSTEM"),
                        true,
                        null,
                        null,
                        List.of(10L, 20L),
                        List.of(30L),
                        List.of("Sakura Store", "Momo Store")));

        mockMvc.perform(get("/api/me").with(user("xizocode@gmail.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("xizocode@gmail.com"))
                .andExpect(jsonPath("$.fullName").value("Xizo Code"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN_SYSTEM"))
                .andExpect(jsonPath("$.activeMarketId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.activeMarketName").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.marketIds[0]").value(10))
                .andExpect(jsonPath("$.storeIds[0]").value(30));
    }

    @Test
    void shouldReturnEmptyArraysWhenUserHasNoAssignedScopes() throws Exception {
        User user = new User();
        user.setId(2L);
        user.setEmail("admin@colaborapp.cl");
        user.setFullName("Admin");

        when(authenticatedUserService.getCurrentUserSnapshot()).thenReturn(
                new AuthenticatedUserService.CurrentAuthenticatedUser(
                        user,
                        List.of(),
                        true,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of()));

        mockMvc.perform(get("/api/me").with(user("admin@colaborapp.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.marketIds").isArray())
                .andExpect(jsonPath("$.storeIds").isArray());
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }
}
