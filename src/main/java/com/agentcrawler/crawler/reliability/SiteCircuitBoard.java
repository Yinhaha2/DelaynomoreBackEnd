package com.agentcrawler.crawler.reliability;

import com.agentcrawler.config.CrawlerReliabilityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;

/**
 * Per-upstream circuit: user traffic never hits OPEN keys. Recovery is background probe only.
 */
public class SiteCircuitBoard {
    private static final Logger log = LoggerFactory.getLogger(SiteCircuitBoard.class);

    private final CrawlerReliabilityProperties properties;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Breaker> breakers = new ConcurrentHashMap<>();

    public SiteCircuitBoard(CrawlerReliabilityProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    public SiteCircuitBoard(CrawlerReliabilityProperties properties, LongSupplier clock) {
        this.properties = properties == null ? CrawlerReliabilityProperties.disabled() : properties;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public static SiteCircuitBoard disabled() {
        return new SiteCircuitBoard(CrawlerReliabilityProperties.disabled());
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public boolean allowRequest(String key) {
        if (!properties.enabled() || key == null || key.isBlank()) {
            return true;
        }
        return breaker(key).allowRequest();
    }

    public void recordSuccess(String key) {
        if (!properties.enabled() || key == null || key.isBlank()) {
            return;
        }
        breaker(key).recordSuccess();
    }

    public void recordFailure(String key) {
        if (!properties.enabled() || key == null || key.isBlank()) {
            return;
        }
        breaker(key).recordFailure();
    }

    public List<String> keysAwaitingProbe() {
        if (!properties.enabled()) {
            return List.of();
        }
        List<String> ready = new ArrayList<>();
        for (var entry : breakers.entrySet()) {
            if (entry.getValue().readyForProbe()) {
                ready.add(entry.getKey());
            }
        }
        return ready;
    }

    public void recordProbeSuccess(String key) {
        Breaker breaker = breakers.get(key);
        if (breaker != null) {
            breaker.probeSuccess();
        }
    }

    public void recordProbeFailure(String key) {
        Breaker breaker = breakers.get(key);
        if (breaker != null) {
            breaker.probeFailure();
        }
    }

    public boolean isOpen(String key) {
        Breaker breaker = breakers.get(key);
        return breaker != null && breaker.open;
    }

    private Breaker breaker(String key) {
        return breakers.computeIfAbsent(key, ignored -> new Breaker(key));
    }

    private final class Breaker {
        private final String key;
        private final ConcurrentLinkedDeque<Sample> samples = new ConcurrentLinkedDeque<>();
        private volatile boolean open;
        private volatile long openedAtMillis;
        private volatile boolean probing;

        private Breaker(String key) {
            this.key = key;
        }

        private synchronized boolean allowRequest() {
            return !open;
        }

        private synchronized void recordSuccess() {
            if (open) {
                return;
            }
            addSample(true);
        }

        private synchronized void recordFailure() {
            if (open) {
                return;
            }
            addSample(false);
            if (shouldOpen()) {
                open = true;
                openedAtMillis = clock.getAsLong();
                probing = false;
                log.warn("熔断打开 {}，用户请求将跳过该上游，{}s 后嗅探", key, properties.openWaitSeconds());
            }
        }

        private synchronized boolean readyForProbe() {
            if (!open || probing) {
                return false;
            }
            long waitMs = Math.max(1, properties.openWaitSeconds()) * 1000L;
            if (clock.getAsLong() - openedAtMillis < waitMs) {
                return false;
            }
            probing = true;
            return true;
        }

        private synchronized void probeSuccess() {
            open = false;
            probing = false;
            samples.clear();
            log.info("熔断关闭 {}，嗅探成功", key);
        }

        private synchronized void probeFailure() {
            openedAtMillis = clock.getAsLong();
            probing = false;
            log.warn("嗅探失败 {}，继续熔断", key);
        }

        private void addSample(boolean success) {
            long now = clock.getAsLong();
            samples.addLast(new Sample(now, success));
            prune(now);
        }

        private void prune(long now) {
            long windowMs = Math.max(1, properties.failureWindowSeconds()) * 1000L;
            while (!samples.isEmpty() && now - samples.peekFirst().atMillis > windowMs) {
                samples.pollFirst();
            }
        }

        private boolean shouldOpen() {
            prune(clock.getAsLong());
            int total = samples.size();
            if (total < properties.minSamples()) {
                return false;
            }
            int failures = 0;
            for (Sample sample : samples) {
                if (!sample.success) {
                    failures++;
                }
            }
            if (failures >= properties.openAfterFailures()) {
                return true;
            }
            return failures / (double) total >= properties.failureRateThreshold();
        }
    }

    private record Sample(long atMillis, boolean success) {}
}
