package com.colaborapp.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.colaborapp.users.domain.RoleCode;

@Component
public class SessionKeepAliveFilter extends OncePerRequestFilter {

    private static final Set<String> EXTENDED_SESSION_ROLES = Set.of(
            RoleCode.ADMIN_MARKET.name(),
            RoleCode.SELLER.name());

    private final int extendedTimeoutSeconds;

    public SessionKeepAliveFilter(
            @Value("${app.security.session.extended-timeout-seconds:2592000}") int extendedTimeoutSeconds) {
        this.extendedTimeoutSeconds = extendedTimeoutSeconds;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        refreshSessionTimeoutIfEligible(request);
        filterChain.doFilter(request, response);
    }

    private void refreshSessionTimeoutIfEligible(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ColaborAppUserPrincipal principal)) {
            return;
        }

        boolean hasExtendedRole = principal.roles().stream().anyMatch(EXTENDED_SESSION_ROLES::contains);
        if (!hasExtendedRole) {
            return;
        }

        if (session.getMaxInactiveInterval() != extendedTimeoutSeconds) {
            session.setMaxInactiveInterval(extendedTimeoutSeconds);
        }
    }
}
