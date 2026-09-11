package com.agentcrawler.crawler.reliability;

import com.agentcrawler.config.CrawlerReliabilityProperties;
import com.agentcrawler.crawler.fallback.SiteFallback;
import com.agentcrawler.crawler.http.SiteHttpClient;
import com.agentcrawler.crawler.plugin.PluginRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpstreamProbeSchedulerTest {

    @Test
    void successfulProbeClosesCircuit() {
        AtomicLong now = new AtomicLong(1_000L);
        SiteCircuitBoard board = new SiteCircuitBoard(CrawlerReliabilityProperties.defaults(), now::get);
        String key = UpstreamKeys.fallback("YHDM");
        for (int i = 0; i < 5; i++) {
            board.recordFailure(key);
        }
        now.addAndGet(180_000L);

        SiteFallback yhdm = mock(SiteFallback.class);
        when(yhdm.name()).thenReturn("YHDM");
        when(yhdm.enabled()).thenReturn(true);
        when(yhdm.probe()).thenReturn(true);

        UpstreamProbeScheduler scheduler = new UpstreamProbeScheduler(
                board,
                mock(PluginRegistry.class),
                new SiteHttpClient(2),
                List.of(yhdm)
        );
        scheduler.probeOpenUpstreams();
        assertThat(board.isOpen(key)).isFalse();
        assertThat(board.allowRequest(key)).isTrue();
    }
}
