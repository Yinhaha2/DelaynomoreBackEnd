package com.agentcrawler.crawler.reliability;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlSingleflightTest {

    @Test
    void overlappingCallsShareOneExecution() throws Exception {
        CrawlSingleflight singleflight = new CrawlSingleflight();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread first = new Thread(() -> singleflight.run("dm84\0芙莉莲", () -> {
            runs.incrementAndGet();
            inFlight.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release timeout");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return "ok";
        }));
        first.start();
        assertThat(inFlight.await(2, TimeUnit.SECONDS)).isTrue();

        Thread second = new Thread(() -> singleflight.run("dm84\0芙莉莲", () -> {
            runs.incrementAndGet();
            return "nope";
        }));
        second.start();
        Thread.sleep(80);
        release.countDown();
        first.join(2000);
        second.join(2000);

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void laterCallRunsAgainAfterFirstCompletes() {
        CrawlSingleflight singleflight = new CrawlSingleflight();
        AtomicInteger runs = new AtomicInteger();
        singleflight.run("k", runs::incrementAndGet);
        singleflight.run("k", runs::incrementAndGet);
        assertThat(runs.get()).isEqualTo(2);
    }
}
