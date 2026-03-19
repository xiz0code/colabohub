package com.colaborapp.config.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public class SecurityRateLimitProperties {

    private final Rule login = new Rule();
    private final Rule pos = new Rule();

    public Rule getLogin() {
        return login;
    }

    public Rule getPos() {
        return pos;
    }

    public static class Rule {

        private boolean enabled;
        private int maxRequests = 30;
        private int windowSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
