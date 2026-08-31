package com.barricador.client;

import java.time.Duration;

/**
 * Immutable SDK configuration, created via {@link BarricadorClient#builder(String)}. Sensible
 * production defaults are provided; only the server SDK key is required.
 */
public final class BarricadorConfig {

    private final String sdkKey;
    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration metricsFlushInterval;
    private final Duration initialReconnectDelay;
    private final Duration maxReconnectDelay;
    private final boolean streamingEnabled;
    private final Duration pollInterval;
    private final boolean metricsEnabled;
    private final Duration startupBootstrapTimeout;

    BarricadorConfig(Builder b) {
        this.sdkKey = b.sdkKey;
        this.baseUrl = b.baseUrl;
        this.connectTimeout = b.connectTimeout;
        this.metricsFlushInterval = b.metricsFlushInterval;
        this.initialReconnectDelay = b.initialReconnectDelay;
        this.maxReconnectDelay = b.maxReconnectDelay;
        this.streamingEnabled = b.streamingEnabled;
        this.pollInterval = b.pollInterval;
        this.metricsEnabled = b.metricsEnabled;
        this.startupBootstrapTimeout = b.startupBootstrapTimeout;
    }

    public String sdkKey() {
        return sdkKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration metricsFlushInterval() {
        return metricsFlushInterval;
    }

    public Duration initialReconnectDelay() {
        return initialReconnectDelay;
    }

    public Duration maxReconnectDelay() {
        return maxReconnectDelay;
    }

    public boolean streamingEnabled() {
        return streamingEnabled;
    }

    /** How often the SDK re-checks the ruleset when streaming is disabled (the default mode). */
    public Duration pollInterval() {
        return pollInterval;
    }

    public boolean metricsEnabled() {
        return metricsEnabled;
    }

    public Duration startupBootstrapTimeout() {
        return startupBootstrapTimeout;
    }

    public static final class Builder {
        private final String sdkKey;
        private String baseUrl = "https://app.barricador.com";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration metricsFlushInterval = Duration.ofSeconds(30);
        private Duration initialReconnectDelay = Duration.ofSeconds(1);
        private Duration maxReconnectDelay = Duration.ofSeconds(60);
        // Polling is the default. An open SSE stream bills backend instance time for its entire
        // lifetime, so streaming every SDK by default made an idle service cost the same as a busy
        // one. Polling costs a 304 every pollInterval; opt back in when sub-second propagation
        // matters more than cost.
        private boolean streamingEnabled = false;
        private Duration pollInterval = Duration.ofSeconds(30);
        private boolean metricsEnabled = true;
        private Duration startupBootstrapTimeout = Duration.ofSeconds(5);

        Builder(String sdkKey) {
            if (sdkKey == null || sdkKey.isBlank()) {
                throw new IllegalArgumentException("Barricador SDK key is required");
            }
            this.sdkKey = sdkKey;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = stripTrailingSlash(baseUrl);
            return this;
        }

        public Builder connectTimeout(Duration d) {
            this.connectTimeout = d;
            return this;
        }

        public Builder metricsFlushInterval(Duration d) {
            this.metricsFlushInterval = d;
            return this;
        }

        public Builder initialReconnectDelay(Duration d) {
            this.initialReconnectDelay = d;
            return this;
        }

        public Builder maxReconnectDelay(Duration d) {
            this.maxReconnectDelay = d;
            return this;
        }

        /**
         * Opt into Server-Sent Events for near-instant flag propagation. Off by default: a held-open
         * stream is billed as continuous backend instance time, while polling is not. Turn this on
         * for kill-switch flags where a delay of up to {@link #pollInterval(Duration)} is not
         * acceptable.
         */
        public Builder streamingEnabled(boolean enabled) {
            this.streamingEnabled = enabled;
            return this;
        }

        /**
         * Interval between ruleset refreshes when streaming is disabled. Default 30s. Unchanged
         * rulesets return {@code 304 Not Modified}, so a short interval is cheap — but it is still a
         * request per interval per process.
         */
        public Builder pollInterval(Duration d) {
            if (d == null || d.isNegative() || d.isZero()) {
                throw new IllegalArgumentException("pollInterval must be positive");
            }
            this.pollInterval = d;
            return this;
        }

        public Builder metricsEnabled(boolean enabled) {
            this.metricsEnabled = enabled;
            return this;
        }

        public Builder startupBootstrapTimeout(Duration d) {
            this.startupBootstrapTimeout = d;
            return this;
        }

        /** Builds the config only (useful for tests). */
        public BarricadorConfig buildConfig() {
            return new BarricadorConfig(this);
        }

        /** Builds the config and starts a fully-initialized {@link BarricadorClient}. */
        public BarricadorClient build() {
            return BarricadorClient.create(buildConfig());
        }

        private static String stripTrailingSlash(String s) {
            return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
        }
    }
}
