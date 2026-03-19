package com.colaborapp.config.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(SecurityRateLimitProperties.class)
public class RateLimitWebConfig {

    @Bean
    InMemoryRateLimitService inMemoryRateLimitService() {
        return new InMemoryRateLimitService();
    }

    @Bean
    HandlerInterceptor rateLimitInterceptor(
            SecurityRateLimitProperties properties,
            InMemoryRateLimitService rateLimitService,
            ObjectMapper objectMapper) {
        return new RateLimitInterceptor(properties, rateLimitService, objectMapper);
    }

    @Bean
    WebMvcConfigurer rateLimitWebMvcConfigurer(HandlerInterceptor rateLimitInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(rateLimitInterceptor);
            }
        };
    }
}
