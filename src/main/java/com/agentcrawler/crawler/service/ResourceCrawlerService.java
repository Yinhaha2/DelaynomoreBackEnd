package com.agentcrawler.crawler.service;

import com.agentcrawler.config.AppProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.crawler.engine.RuleEngine;
import com.agentcrawler.crawler.media.MediaExtractor;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.crawler.plugin.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ResourceCrawlerService(
            PluginRegistry pluginRegistry,
            RuleEngine ruleEngine,
            MediaExtractor mediaExtractor,
            AppProperties properties
    ) {
        this.pluginRegistry = pluginRegistry;
        this.ruleEngine = ruleEngine;
        this.mediaExtractor = mediaExtractor;
        this.properties = properties;
    }

    public CrawlResourceResult crawl(String keyword, String site) {
        List<PluginRule> candidates = resolveCandidates(site);
        if (candidates.isEmpty()) {
            return CrawlResourceResult.failed(
                    keyword,
                    site,
                    site,
                    "当前没有可用的检索站点，请稍后再试。"
            );
        }

        Exception lastError = null;
        for (PluginRule rule : candidates) {
            try {
                return crawlWithRule(keyword, site, rule);
            } catch (Exception ex) {
                lastError = ex;
                log.warn("站点 {} 检索「{}」失败: {}", rule.getName(), keyword, userFacingMessage(ex));
            }
        }

        String pluginName = candidates.get(0).getName();
        return CrawlResourceResult.failed(keyword, site, pluginName, userFacingMessage(lastError));
    }

    public String availableSites() {
        List<String> names = pluginRegistry.usableNames();
        return names.isEmpty() ? "暂无可用站点" : String.join(", ", names);
    }

    private List<PluginRule> resolveCandidates(String site) {
        List<PluginRule> usable = pluginRegistry.usable();
        if (site == null || site.isBlank()) {
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
                        String pageHtml = ruleEngine.fetchPage(rule, episodeUrl);
                        for (String videoUrl : mediaExtractor.extractVideoUrls(pageHtml, episodeUrl)) {
                            addVideo(videos, seenVideoUrls, episodeName, videoUrl, episodeUrl, road.name());
                        }
                        for (String imageUrl : mediaExtractor.extractImageUrls(pageHtml, episodeUrl)) {
                            addImage(images, seenImageUrls, imageUrl, episodeName);
                        }
                        for (String linkUrl : mediaExtractor.extractPageLinks(pageHtml, episodeUrl)) {
                            if (isDirectMediaUrl(linkUrl)) {
                                addVideo(videos, seenVideoUrls, episodeName, linkUrl, episodeUrl, road.name());
                            }
                        }
                    } catch (Exception ignored) {
                        // 单个剧集页失败时跳过，继续处理其他线路/剧集
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
        if (seen.add(url)) {
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
