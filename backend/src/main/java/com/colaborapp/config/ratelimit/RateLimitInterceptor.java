package com.colaborapp.config.ratelimit;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import com.colaborapp.common.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RateLimitInterceptor implements HandlerInterceptor {

    private final SecurityRateLimitProperties properties;
    private final InMemoryRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(
            SecurityRateLimitProperties properties,
            InMemoryRateLimitService rateLimitService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/oauth2/authorization/")) {
            return handleRule(response, request, "login", properties.getLogin());
        }
        if (uri.startsWith("/api/pos/sales") && !"GET".equalsIgnoreCase(request.getMethod())) {
            return handleRule(response, request, "pos", properties.getPos());
        }
        return true;
    }

    private boolean handleRule(
            HttpServletResponse response,
            HttpServletRequest request,
            String bucket,
            SecurityRateLimitProperties.Rule rule) throws IOException {
        if (!rule.isEnabled()) {
            return true;
        }

        String key = bucket + ":" + request.getRemoteAddr();
        boolean allowed = rateLimitService.allow(
                key,
                rule.getMaxRequests(),
                Duration.ofSeconds(rule.getWindowSeconds()));
        if (allowed) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Demasiadas solicitudes",
                "Detectamos demasiados intentos seguidos. Espera un momento antes de intentarlo nuevamente."));
        return false;
    }
}
