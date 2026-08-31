package com.barricador.client.internal;

import com.barricador.client.BarricadorConfig;
import com.barricador.client.model.FlagModels.BootstrapResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP layer over the JDK {@link HttpClient} for the request/response calls: the startup
 * bootstrap, the polling refresh, and the periodic metrics flush. The opt-in long-lived SSE stream is
 * handled by {@link StreamSynchronizer}, which shares this client.
 */
public final class HttpTransport {

    private final BarricadorConfig config;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public HttpTransport(BarricadorConfig config, ObjectMapper mapper, HttpClient httpClient) {
        this.config = config;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    HttpClient httpClient() {
        return httpClient;
    }

    BarricadorConfig config() {
        return config;
    }

    /**
     * Result of a (possibly conditional) bootstrap. When {@link #notModified()} is true the server
     * answered 304 and {@link #body()} is null — the cached ruleset is still current.
     */
    public record BootstrapResult(boolean notModified, String etag, BootstrapResponse body) {
    }

    /** Fetches the full environment ruleset unconditionally. Throws on any non-2xx or transport failure. */
    public BootstrapResponse bootstrap() throws Exception {
        BootstrapResult result = bootstrap(null);
        return result.body();
    }

    /**
     * Fetches the ruleset, sending {@code If-None-Match} when {@code etag} is non-null.
     *
     * <p>This is the polling path. An unchanged poll returns 304 with no body, which costs the
     * backend one already-loaded document comparison and no flag reads — that is what makes polling
     * cheaper than holding a stream open, which bills server instance time for its whole lifetime.
     */
    public BootstrapResult bootstrap(String etag) throws Exception {
        HttpRequest.Builder builder = baseRequest("/api/v1/flags/bootstrap")
                .timeout(config.startupBootstrapTimeout())
                .GET();
        if (etag != null && !etag.isBlank()) {
            builder.header("If-None-Match", etag);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 304) {
            return new BootstrapResult(true, etag, null);
        }
        if (status / 100 != 2) {
            throw new IllegalStateException("Bootstrap failed: HTTP " + status);
        }
        String newEtag = response.headers().firstValue("ETag").orElse(null);
        return new BootstrapResult(false, newEtag, mapper.readValue(response.body(), BootstrapResponse.class));
    }

    /** Posts a batch of aggregated metrics. Returns true on success (failures are swallowed by caller). */
    public boolean flushMetrics(List<MetricsBuffer.MetricEvent> events) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("events", events);
        HttpRequest request = baseRequest("/api/v1/metrics/flush")
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode() / 100 == 2;
    }

    HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                .header("Authorization", "Bearer " + config.sdkKey())
                .header("Accept", "application/json");
    }
}
