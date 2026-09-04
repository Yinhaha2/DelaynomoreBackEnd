package com.agentcrawler.crawler.service;

import com.agentcrawler.config.AppProperties;
import com.agentcrawler.crawler.engine.RuleEngine;
import com.agentcrawler.crawler.media.MediaExtractor;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.crawler.plugin.PluginRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ResourceCrawlerService {
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
        PluginRule rule = pluginRegistry.resolve(site);
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

            List<Road> roads = ruleEngine.queryChapters(rule, item.src());
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
                site,
                rule.getName(),
                videos,
                links,
                images
        );
    }

    public String availableSites() {
        return String.join(", ", pluginRegistry.all().stream().map(PluginRule::getName).toList());
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
