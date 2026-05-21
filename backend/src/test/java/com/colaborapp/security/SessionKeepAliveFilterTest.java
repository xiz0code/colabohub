package com.colaborapp.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionKeepAliveFilterTest {

    @Test
    void extendsSessionTimeoutForSeller() throws Exception {
        SessionKeepAliveFilter filter = new SessionKeepAliveFilter(2592000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pos/sales/open");
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(1800);
        request.setSession(session);

        ColaborAppUserPrincipal principal = new ColaborAppUserPrincipal(
                1L,
                "seller@colabohub.cl",
                "Seller",
                List.of("SELLER"),
                List.of(),
                List.of(),
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        try {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(session.getMaxInactiveInterval()).isEqualTo(2592000);
    }

    @Test
    void leavesDefaultSessionTimeoutForStoreUser() throws Exception {
        SessionKeepAliveFilter filter = new SessionKeepAliveFilter(2592000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(1800);
        request.setSession(session);

        ColaborAppUserPrincipal principal = new ColaborAppUserPrincipal(
                2L,
                "store@colabohub.cl",
                "Store User",
                List.of("STORE_USER"),
                List.of(),
                List.of(),
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        try {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
    }
}
