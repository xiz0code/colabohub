package com.colaborapp.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class InMemoryRateLimitServiceTest {

    @Test
    void shouldAllowRequestsWithinWindowAndBlockWhenExceeded() {
        Instant now = Instant.parse("2026-03-18T23:00:00Z");
        InMemoryRateLimitService service = new InMemoryRateLimitService(Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.allow("login:127.0.0.1", 2, Duration.ofSeconds(60))).isTrue();
        assertThat(service.allow("login:127.0.0.1", 2, Duration.ofSeconds(60))).isTrue();
        assertThat(service.allow("login:127.0.0.1", 2, Duration.ofSeconds(60))).isFalse();
    }
}
