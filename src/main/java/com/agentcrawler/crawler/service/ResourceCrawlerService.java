package com.agentcrawler.crawler.service;

import com.agentcrawler.config.AppProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.crawler.engine.RuleEngine;
import com.agentcrawler.crawler.fallback.SiteFallback;
import com.agentcrawler.crawler.media.MediaExtractor;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.crawler.plugin.PluginRegistry;
import com.agentcrawler.crawler.reliability.CrawlSingleflight;
import com.agentcrawler.crawler.reliability.SiteCircuitBoard;
import com.agentcrawler.crawler.reliability.UpstreamFailureClassifier;
import com.agentcrawler.crawler.reliability.UpstreamKeys;
import com.agentcrawler.crawler.webview.FetchedPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ResourceCrawlerService {
    private static final Logger log = LoggerFactory.getLogger(ResourceCrawlerService.class);

    private final PluginRegistry pluginRegistry;
    private final RuleEngine ruleEngine;
    private final MediaExtractor mediaExtractor;
    private final AppProperties properties;
    private final List<SiteFallback> fallbacks;
    private final SiteCircuitBoard circuitBoard;
    private final CrawlSingleflight singleflight;

    public ResourceCrawlerService(
            PluginRegistry pluginRegistry,
            RuleEngine ruleEngine,
            MediaExtractor mediaExtractor,
            AppProperties properties
    ) {
        this(pluginRegistry, ruleEngine, mediaExtractor, properties, List.of());
    }

    public ResourceCrawlerService(
            PluginRegistry pluginRegistry,
            RuleEngine ruleEngine,
            MediaExtractor mediaExtractor,
            AppProperties properties,
            List<SiteFallback> fallbacks
    ) {
        this(
                pluginRegistry,
                ruleEngine,
                mediaExtractor,
                properties,
                fallbacks,
                SiteCircuitBoard.disabled(),
                CrawlSingleflight.direct()
        );
    }

    @Autowired
    public ResourceCrawlerService(
            PluginRegistry pluginRegistry,
            RuleEngine ruleEngine,
            MediaExtractor mediaExtractor,
            AppProperties properties,
            List<SiteFallback> fallbacks,
            SiteCircuitBoard circuitBoard,
            CrawlSingleflight singleflight
    ) {
        this.pluginRegistry = pluginRegistry;
        this.ruleEngine = ruleEngine;
        this.mediaExtractor = mediaExtractor;
        this.properties = properties;
        this.fallbacks = fallbacks == null ? List.of() : List.copyOf(fallbacks);
        this.circuitBoard = circuitBoard == null ? SiteCircuitBoard.disabled() : circuitBoard;
        this.singleflight = singleflight == null ? CrawlSingleflight.direct() : singleflight;
    }

    public CrawlResourceResult crawl(String keyword, String site) {
        return singleflight.run(CrawlSingleflight.key(site, keyword), () -> crawlUncoalesced(keyword, site));
    }

    CrawlResourceResult crawlUncoalesced(String keyword, String site) {
        int skipped = 0;
        SiteFallback dedicated = findFallback(site);
        if (dedicated != null && dedicated.enabled()) {
            if (!circuitBoard.allowRequest(UpstreamKeys.fallback(dedicated.name()))) {
                skipped++;
                log.info("跳过熔断中的降级站点 {}", dedicated.name());
            } else {
                CrawlResourceResult dedicatedResult = safeFallback(dedicated, keyword);
                if (hasVideos(dedicatedResult)) {
                    return dedicatedResult;
                }
            }
        }

        CrawlResourceResult pluginBest = crawlPlugins(keyword, site);
        if (hasVideos(pluginBest)) {
            return pluginBest;
        }

        for (SiteFallback fallback : fallbacks) {
            if (!fallback.enabled() || (dedicated != null && fallback.name().equals(dedicated.name()))) {
                continue;
            }
            if (!circuitBoard.allowRequest(UpstreamKeys.fallback(fallback.name()))) {
                skipped++;
                log.info("跳过熔断中的降级站点 {}", fallback.name());
                continue;
            }
            log.info("插件未拿到播放地址，开始降级到 {}", fallback.name());
            CrawlResourceResult result = safeFallback(fallback, keyword);
            if (hasVideos(result)) {
                log.info("已从 {} 拿到播放地址", fallback.name());
                return result;
            }
            if (!hasResources(pluginBest) && hasResources(result)) {
                pluginBest = result;
            }
        }

        if (hasResources(pluginBest)) {
            return pluginBest;
        }
        if (pluginBest != null && pluginBest.error() != null) {
            return pluginBest;
        }
        String message = skipped > 0 && circuitBoard.enabled()
                ? "检索站点暂时不稳定，请稍后再试。"
                : "暂时没有找到可用资源，请稍后再试。";
        return CrawlResourceResult.failed(keyword, site, site, message);
    }

    public String availableSites() {
        List<String> names = new ArrayList<>(pluginRegistry.usableNames());
        for (SiteFallback fallback : fallbacks) {
            if (fallback.enabled() && names.stream().noneMatch(name -> name.equalsIgnoreCase(fallback.name()))) {
                names.add(fallback.name());
            }
        }
        return names.isEmpty() ? "暂无可用站点" : String.join(", ", names);
    }

    private CrawlResourceResult crawlPlugins(String keyword, String site) {
        List<PluginRule> candidates = resolveCandidates(site);
        if (candidates.isEmpty()) {
            return null;
        }
        CrawlResourceResult best = null;
        Exception lastError = null;
        int skipped = 0;
        for (PluginRule rule : candidates) {
            String key = UpstreamKeys.plugin(rule.getName());
            if (!circuitBoard.allowRequest(key)) {
                skipped++;
                log.info("跳过熔断中的插件 {}", rule.getName());
                continue;
            }
            try {
                CrawlResourceResult result = crawlWithRule(keyword, site, rule);
                recordUpstreamOutcome(key, result, null);
                if (hasVideos(result)) {
                    return result;
                }
                if (best == null || (!hasResources(best) && hasResources(result))) {
                    best = result;
                }
            } catch (Exception ex) {
                lastError = ex;
                recordUpstreamOutcome(key, null, ex);
                log.warn("站点 {} 检索「{}」失败: {}", rule.getName(), keyword, userFacingMessage(ex));
            }
        }
        if (best != null) {
            return best;
        }
        if (skipped > 0 && skipped == candidates.size()) {
            return CrawlResourceResult.failed(keyword, site, candidates.get(0).getName(), "检索站点暂时不稳定，请稍后再试。");
        }
        return CrawlResourceResult.failed(keyword, site, candidates.get(0).getName(), userFacingMessage(lastError));
    }

    private CrawlResourceResult safeFallback(SiteFallback fallback, String keyword) {
        String key = UpstreamKeys.fallback(fallback.name());
        try {
            CrawlResourceResult result = fallback.crawl(
                    keyword,
                    properties.crawler().maxSearchResults(),
                    properties.crawler().maxEpisodesPerRoad()
            );
            recordUpstreamOutcome(key, result, null);
            return result;
        } catch (Exception ex) {
            recordUpstreamOutcome(key, null, ex);
            log.warn("降级站点 {} 检索「{}」失败: {}", fallback.name(), keyword, userFacingMessage(ex));
            return CrawlResourceResult.failed(keyword, fallback.name(), fallback.name(), userFacingMessage(ex));
        }
    }

    private void recordUpstreamOutcome(String key, CrawlResourceResult result, Exception error) {
        if (error != null) {
            if (UpstreamFailureClassifier.isInfrastructureFailure(error)) {
                circuitBoard.recordFailure(key);
            } else {
                circuitBoard.recordSuccess(key);
            }
            return;
        }
        if (UpstreamFailureClassifier.isInfrastructureFailure(result)) {
            circuitBoard.recordFailure(key);
        } else {
            circuitBoard.recordSuccess(key);
        }
    }

    private SiteFallback findFallback(String site) {
        if (site == null || site.isBlank()) {
            return null;
        }
        for (SiteFallback fallback : fallbacks) {
            if (fallback.matches(site)) {
                return fallback;
            }
        }
        return null;
    }

    private List<PluginRule> resolveCandidates(String site) {
        List<PluginRule> usable = pluginRegistry.usable();
        if (site == null || site.isBlank()) {
            return usable;
        }
        if (findFallback(site) != null) {
            return usable;
        }
        try {
            PluginRule requested = pluginRegistry.resolve(site);
            List<PluginRule> ordered = new ArrayList<>();
            ordered.add(requested);
            for (PluginRule rule : usable) {
                if (!rule.getName().equalsIgnoreCase(requested.getName())) {
                    ordered.add(rule);
                }
            }
            return ordered;
        } catch (AppException ex) {
            return usable;
        }
    }

    private CrawlResourceResult crawlWithRule(String keyword, String requestedSite, PluginRule rule) {
        List<SearchItem> searchItems = ruleEngine.search(rule, keyword);

        List<CrawlResourceResult.VideoResource> videos = new ArrayList<>();
        List<CrawlResourceResult.LinkResource> links = new ArrayList<>();
        List<CrawlResourceResult.ImageResource> images = new ArrayList<>();
        Set<String> seenVideoUrls = new LinkedHashSet<>();
        Set<String> seenLinkUrls = new LinkedHashSet<>();
        Set<String> seenImageUrls = new LinkedHashSet<>();

        int maxResults = properties.crawler().maxSearchResults();
        int maxEpisodes = properties.crawler().maxEpisodesPerRoad();

        for (SearchItem item : searchItems.stream().limit(maxResults).toList()) {
            addLink(links, seenLinkUrls, item.name(), item.src(), "搜索结果");
            addImage(images, seenImageUrls, item.imageUrl(), item.name());

            List<Road> roads;
            try {
                roads = ruleEngine.queryChapters(rule, item.src());
            } catch (Exception ex) {
                log.warn("站点 {} 解析剧集失败: {}", rule.getName(), userFacingMessage(ex));
                continue;
            }
            for (Road road : roads) {
                int episodeCount = Math.min(road.episodeUrls().size(), maxEpisodes);
                for (int i = 0; i < episodeCount; i++) {
                    String episodeName = road.episodeNames().get(i);
                    String episodeUrl = road.episodeUrls().get(i);
                    addLink(links, seenLinkUrls, episodeName, episodeUrl, road.name());

                    if (isDirectMediaUrl(episodeUrl)) {
                        addVideo(videos, seenVideoUrls, episodeName, episodeUrl, episodeUrl, road.name());
                        continue;
                    }

                    try {
                        FetchedPage page = ruleEngine.fetchPageDetailed(rule, episodeUrl);
                        String pageHtml = page.html();
                        for (String videoUrl : mediaExtractor.extractVideoUrls(pageHtml, episodeUrl)) {
                            addVideo(videos, seenVideoUrls, episodeName, videoUrl, episodeUrl, road.name());
                        }
                        for (String captured : page.mediaUrls()) {
                            if (captured != null && !captured.isBlank()) {
                                addVideo(videos, seenVideoUrls, episodeName, captured, episodeUrl, road.name());
                            }
                        }
                        for (String imageUrl : mediaExtractor.extractImageUrls(pageHtml, episodeUrl)) {
                            addImage(images, seenImageUrls, imageUrl, episodeName);
                        }
                        for (String linkUrl : mediaExtractor.extractPageLinks(pageHtml, episodeUrl)) {
                            if (isDirectMediaUrl(linkUrl)) {
                                addVideo(videos, seenVideoUrls, episodeName, linkUrl, episodeUrl, road.name());
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("站点 {} 剧集页抓取失败 {}: {}", rule.getName(), episodeUrl, userFacingMessage(ex));
                    }
                }
            }
        }

        return new CrawlResourceResult(
                keyword,
                requestedSite,
                rule.getName(),
                videos,
                links,
                images
        );
    }

    public static String userFacingMessage(Throwable error) {
        if (error == null) {
            return "暂时没有找到可用资源，请稍后再试。";
        }
        String raw = error.getMessage() == null ? "" : error.getMessage();
        String combined = raw + " " + String.valueOf(error.getCause());
        if (containsAny(combined, "522", "521", "523", "524")) {
            return "检索站点暂时无法访问，请稍后再试。";
        }
        if (containsAny(combined, "502", "503", "504", "timeout", "timed out", "ConnectException")) {
            return "检索站点响应超时，请稍后再试。";
        }
        if (containsAny(combined, "403", "429")) {
            return "检索站点暂时限制访问，请稍后再试。";
        }
        if (containsAny(combined, "searchURL", "this.text")) {
            return "当前站点规则不可用，请稍后再试。";
        }
        if (error instanceof AppException && !raw.isBlank() && !raw.contains("http://") && !raw.contains("https://")) {
            return raw;
        }
        return "暂时没有找到可用资源，请稍后再试。";
    }

    private static boolean hasVideos(CrawlResourceResult result) {
        return result != null && result.videos() != null && !result.videos().isEmpty();
    }

    private static boolean hasResources(CrawlResourceResult result) {
        return result != null && result.hasResources();
    }

    private static boolean containsAny(String text, String... tokens) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDirectMediaUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains(".m3u8") || lower.contains(".mp4");
    }

    private static void addVideo(
            List<CrawlResourceResult.VideoResource> videos,
            Set<String> seen,
            String title,
            String url,
            String sourcePage,
            String roadName
    ) {
        if (url != null && !url.isBlank() && seen.add(url)) {
            videos.add(new CrawlResourceResult.VideoResource(title, url, sourcePage, roadName));
        }
    }

    private static void addLink(
            List<CrawlResourceResult.LinkResource> links,
            Set<String> seen,
            String title,
            String url,
            String description
    ) {
        if (url == null || url.isBlank() || !seen.add(url)) {
            return;
        }
        links.add(new CrawlResourceResult.LinkResource(title, url, description));
    }

    private static void addImage(
            List<CrawlResourceResult.ImageResource> images,
            Set<String> seen,
            String url,
            String alt
    ) {
        if (url == null || url.isBlank() || !seen.add(url)) {
            return;
        }
        images.add(new CrawlResourceResult.ImageResource(url, alt));
    }
}
