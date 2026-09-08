package com.agentcrawler.crawler.fallback.silisili;

import com.agentcrawler.config.AppProperties;
import com.agentcrawler.crawler.fallback.FallbackHttp;
import com.agentcrawler.crawler.fallback.SiteFallback;
import com.agentcrawler.crawler.http.SiteHttpClient;
import com.agentcrawler.crawler.media.MediaExtractor;
import com.agentcrawler.crawler.media.MediaUrls;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SilisiliSiteFallback implements SiteFallback {
    private static final Logger log = LoggerFactory.getLogger(SilisiliSiteFallback.class);

    private final SiteHttpClient httpClient;
    private final MediaExtractor mediaExtractor;
    private final AppProperties.Crawler.Fallback.Silisili settings;
    private final boolean masterEnabled;

    public SilisiliSiteFallback(
            SiteHttpClient httpClient,
            MediaExtractor mediaExtractor,
            AppProperties properties
    ) {
        this.httpClient = httpClient;
        this.mediaExtractor = mediaExtractor;
        AppProperties.Crawler.Fallback fallback = properties.crawler().fallback();
        this.masterEnabled = fallback.enabled();
        this.settings = fallback.silisili();
    }

    @Override
    public String name() {
        return "SiliSili";
    }

    @Override
    public boolean enabled() {
        return masterEnabled && settings.enabled();
    }

    @Override
    public boolean matches(String site) {
        if (site == null || site.isBlank()) {
            return false;
        }
        String lower = site.toLowerCase(Locale.ROOT);
        return lower.contains("sili")
                || lower.contains("imomoe")
                || site.contains("嘶哩");
    }

    @Override
    public CrawlResourceResult crawl(String keyword, int maxSearchResults, int maxEpisodesPerRoad) {
        String base = FallbackHttp.trimSlash(settings.baseUrl());
        Map<String, String> headers = FallbackHttp.headers(base, settings.cookie());
        try {
            String searchUrl = searchUrl(base, keyword);
            String searchHtml = FallbackHttp.getFollowingHops(httpClient, searchUrl, headers);
            List<SearchItem> items = SilisiliHtmlParser.parseSearch(searchHtml, base + "/");
            if (items.isEmpty()) {
                return CrawlResourceResult.failed(keyword, name(), name(), "SiliSili 未找到匹配结果");
            }

            List<CrawlResourceResult.VideoResource> videos = new ArrayList<>();
            List<CrawlResourceResult.LinkResource> links = new ArrayList<>();
            List<CrawlResourceResult.ImageResource> images = new ArrayList<>();
            Set<String> seenVideo = new LinkedHashSet<>();
            Set<String> seenLink = new LinkedHashSet<>();
            Set<String> seenImage = new LinkedHashSet<>();

            for (SearchItem item : items.stream().limit(maxSearchResults).toList()) {
                addLink(links, seenLink, item.name(), item.src(), "搜索结果");
                addImage(images, seenImage, item.imageUrl(), item.name());
                List<Road> roads;
                try {
                    String detailHtml = FallbackHttp.getFollowingHops(httpClient, item.src(), headers);
                    roads = SilisiliHtmlParser.parseChapters(detailHtml, base + "/");
                } catch (Exception ex) {
                    log.warn("SiliSili 详情失败 {}: {}", item.src(), ex.getMessage());
                    continue;
                }
                for (Road road : roads) {
                    int count = Math.min(road.episodeUrls().size(), maxEpisodesPerRoad);
                    for (int i = 0; i < count; i++) {
                        String episodeName = road.episodeNames().get(i);
                        String episodeUrl = road.episodeUrls().get(i);
                        addLink(links, seenLink, episodeName, episodeUrl, road.name());
                        collectPlayUrl(episodeUrl, episodeName, road.name(), headers, videos, seenVideo);
                    }
                }
            }
            return new CrawlResourceResult(keyword, name(), name(), videos, links, images);
        } catch (Exception ex) {
            log.warn("SiliSili 检索「{}」失败: {}", keyword, ex.getMessage());
            return CrawlResourceResult.failed(keyword, name(), name(), "SiliSili 暂时无法访问，请稍后再试。");
        }
    }

    private void collectPlayUrl(
            String episodeUrl,
            String episodeName,
            String roadName,
            Map<String, String> headers,
            List<CrawlResourceResult.VideoResource> videos,
            Set<String> seenVideo
    ) {
        try {
            String posted = httpClient.postForm(episodeUrl, Map.of("player", "sili"), headers);
            var decoded = SilisiliPlayerDecoder.decode(posted);
            if (decoded.isPresent() && !decoded.get().url().isBlank()) {
                addVideo(videos, seenVideo, episodeName, decoded.get().url(), episodeUrl, roadName);
                return;
            }
            String html = FallbackHttp.getFollowingHops(httpClient, episodeUrl, headers);
            for (String video : mediaExtractor.extractVideoUrls(html, episodeUrl)) {
                addVideo(videos, seenVideo, episodeName, video, episodeUrl, roadName);
            }
            for (String video : MediaUrls.harvestAll(posted + "\n" + html)) {
                addVideo(videos, seenVideo, episodeName, video, episodeUrl, roadName);
            }
        } catch (Exception ex) {
            log.warn("SiliSili 播放页失败 {}: {}", episodeUrl, ex.getMessage());
        }
    }

    private static String searchUrl(String base, String keyword) {
        HttpUrl parsed = HttpUrl.parse(base + "/");
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid SiliSili base URL");
        }
        return parsed.newBuilder()
                .addPathSegment("vodsearch")
                .addQueryParameter("wd", keyword)
                .addQueryParameter("page", "1")
                .build()
                .toString();
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
        if (url != null && !url.isBlank() && seen.add(url)) {
            links.add(new CrawlResourceResult.LinkResource(title, url, description));
        }
    }

    private static void addImage(
            List<CrawlResourceResult.ImageResource> images,
            Set<String> seen,
            String url,
            String alt
    ) {
        if (url != null && !url.isBlank() && seen.add(url)) {
            images.add(new CrawlResourceResult.ImageResource(url, alt));
        }
    }
}
