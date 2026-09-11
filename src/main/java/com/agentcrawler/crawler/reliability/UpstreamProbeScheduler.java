package com.agentcrawler.crawler.reliability;

import com.agentcrawler.config.CrawlerReliabilityProperties;
import com.agentcrawler.crawler.fallback.FallbackHttp;
import com.agentcrawler.crawler.fallback.SiteFallback;
import com.agentcrawler.crawler.http.SiteHttpClient;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.plugin.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Recovers OPEN circuits off the user path. One canary GET per upstream, never a full playlist crawl.
 */
@Component
@ConditionalOnProperty(prefix = "agent.crawler.reliability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UpstreamProbeScheduler {
    private static final Logger log = LoggerFactory.getLogger(UpstreamProbeScheduler.class);

    private final SiteCircuitBoard circuitBoard;
    private final PluginRegistry pluginRegistry;
    private final SiteHttpClient httpClient;
    private final List<SiteFallback> fallbacks;

    public UpstreamProbeScheduler(
            SiteCircuitBoard circuitBoard,
            PluginRegistry pluginRegistry,
            SiteHttpClient httpClient,
            List<SiteFallback> fallbacks
    ) {
        this.circuitBoard = circuitBoard;
        this.pluginRegistry = pluginRegistry;
        this.httpClient = httpClient;
        this.fallbacks = fallbacks == null ? List.of() : fallbacks;
    }

    @Scheduled(
            fixedDelayString = "${agent.crawler.reliability.probe-interval-seconds:30}",
            timeUnit = TimeUnit.SECONDS
    )
    public void probeOpenUpstreams() {
        if (!circuitBoard.enabled()) {
            return;
        }
        for (String key : circuitBoard.keysAwaitingProbe()) {
            boolean ok = probe(key);
            if (ok) {
                circuitBoard.recordProbeSuccess(key);
            } else {
                circuitBoard.recordProbeFailure(key);
            }
        }
    }

    boolean probe(String key) {
        try {
            if (UpstreamKeys.isFallback(key)) {
                return probeFallback(UpstreamKeys.nameOf(key));
            }
            if (UpstreamKeys.isPlugin(key)) {
                return probePlugin(UpstreamKeys.nameOf(key));
            }
            return false;
        } catch (Exception ex) {
            log.debug("嗅探 {} 异常: {}", key, ex.toString());
            return false;
        }
    }

    private boolean probeFallback(String name) {
        for (SiteFallback fallback : fallbacks) {
            if (fallback.name().equalsIgnoreCase(name) && fallback.enabled()) {
                return fallback.probe();
            }
        }
        return false;
    }

    private boolean probePlugin(String name) {
        try {
            PluginRule rule = pluginRegistry.resolve(name);
            String url = rule.getBaseURL();
            if (url == null || url.isBlank()) {
                return false;
            }
            Map<String, String> headers = FallbackHttp.headers(url, rule.getCookie());
            if (rule.getUserAgent() != null && !rule.getUserAgent().isBlank()) {
                headers.put("User-Agent", rule.getUserAgent());
            }
            return httpClient.probeGet(url, headers);
        } catch (Exception ex) {
            return false;
        }
    }
}
