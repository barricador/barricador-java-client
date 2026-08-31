package com.barricador.client.internal;

import com.barricador.client.model.FlagModels.BootstrapResponse;
import com.barricador.client.model.FlagModels.FeatureFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the {@link FlagStore} fresh by re-fetching {@code /api/v1/flags/bootstrap} on a fixed
 * interval, using {@code If-None-Match} so an unchanged ruleset costs one 304 and no payload.
 *
 * <p>This is the default synchronization mode. It trades propagation latency (up to one poll
 * interval) for cost: an open SSE stream bills backend instance time continuously, whereas a poll
 * occupies the server for a few tens of milliseconds. Applications that need sub-second propagation
 * can opt back into streaming with {@code streamingEnabled(true)}.
 *
 * <p>Like the stream synchronizer, this never throws into the host application: a failed poll leaves
 * the last cached ruleset in place and is retried on the next tick.
 */
public final class PollSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(PollSynchronizer.class);

    private final HttpTransport transport;
    private final FlagStore store;
    private final Duration interval;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile String etag;

    public PollSynchronizer(HttpTransport transport, FlagStore store, Duration interval) {
        this.transport = transport;
        this.store = store;
        this.interval = interval;
    }

    /** Seeds the validator from the startup bootstrap so the first poll can already return 304. */
    public void seedEtag(String etag) {
        this.etag = etag;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this::runLoop, "barricador-poll");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runLoop() {
        while (running.get()) {
            // Jitter spreads reconnect-storms across a fleet so N instances don't poll in lockstep.
            if (!sleep(withJitter(interval))) {
                return;
            }
            if (!running.get()) {
                return;
            }
            pollOnce();
        }
    }

    private void pollOnce() {
        try {
            HttpTransport.BootstrapResult result = transport.bootstrap(etag);
            if (result.notModified()) {
                return;
            }
            BootstrapResponse body = result.body();
            if (body == null) {
                return;
            }
            Map<String, FeatureFlag> map = new HashMap<>();
            if (body.flags != null) {
                body.flags.forEach(f -> map.put(f.key, f));
            }
            store.replaceAll(map, body.rulesVersion);
            etag = result.etag();
            log.debug("Barricador poll applied {} flags (v{})", map.size(), body.rulesVersion);
        } catch (Exception e) {
            log.debug("Barricador poll failed ({}); keeping cached ruleset", e.getMessage());
        }
    }

    private Duration withJitter(Duration base) {
        long millis = base.toMillis();
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, millis / 10));
        return Duration.ofMillis(millis + jitter);
    }

    /** @return false when interrupted (shutting down) */
    private boolean sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
