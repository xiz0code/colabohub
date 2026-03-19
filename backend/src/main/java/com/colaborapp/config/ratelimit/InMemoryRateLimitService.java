package com.colaborapp.config.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRateLimitService {

    private final Map<String, Deque<Instant>> requestsByKey = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimitService() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimitService(Clock clock) {
        this.clock = clock;
    }

    public boolean allow(String key, int maxRequests, Duration window) {
        Instant now = clock.instant();
        Instant threshold = now.minus(window);
        Deque<Instant> requests = requestsByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (requests) {
            while (!requests.isEmpty() && requests.peekFirst().isBefore(threshold)) {
                requests.removeFirst();
            }

            if (requests.size() >= maxRequests) {
                return false;
            }

            requests.addLast(now);
            return true;
        }
    }
}
