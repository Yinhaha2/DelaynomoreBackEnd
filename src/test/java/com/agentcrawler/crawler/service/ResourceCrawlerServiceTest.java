package com.agentcrawler.crawler.service;

import com.agentcrawler.config.AppProperties;
import com.agentcrawler.config.CrawlerCacheProperties;
import com.agentcrawler.config.CrawlerReliabilityProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.crawler.engine.RuleEngine;
import com.agentcrawler.crawler.media.MediaExtractor;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.crawler.plugin.PluginRegistry;
import com.agentcrawler.crawler.fallback.SiteFallback;
import com.agentcrawler.crawler.cache.CaffeineCrawlerResultCache;
import com.agentcrawler.crawler.cache.CrawlCacheKeys;
import com.agentcrawler.crawler.reliability.CrawlSingleflight;
import com.agentcrawler.crawler.reliability.SiteCircuitBoard;
import com.agentcrawler.crawler.reliability.UpstreamKeys;
import com.agentcrawler.crawler.webview.FetchedPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceCrawlerServiceTest {

    @Test
    void userFacingMessageHidesHttp522Url() {
        String message = ResourceCrawlerService.userFacingMessage(
                new AppException(ErrorCode.CRAWL_FAILED, "搜索失败: HTTP 522 for https://dmbus.cc/s----------.html?wd=x")
        );
        assertEquals("检索站点暂时无法访问，请稍后再试。", message);
        assertFalse(message.contains("http"));
        assertFalse(message.contains("522"));
    }

    @Test
    void userFacingMessageHidesNullTextNpe() {
        String message = ResourceCrawlerService.userFacingMessage(
                new NullPointerException("Cannot invoke \"java.lang.CharSequence.length()\" because \"this.text\" is null")
        );
        assertEquals("当前站点规则不可用，请稍后再试。", message);
    }

    @Test
    void userFacingMessageHidesTimeout() {
        String message = ResourceCrawlerService.userFacingMessage(
                new IOException("HTTP 504 for https://example.com")
        );
        assertEquals("检索站点响应超时，请稍后再试。", message);
    }

    @Test
    void crawlMergesWebViewCapturedMediaUrls() throws Exception {
        PluginRegistry registry = mock(PluginRegistry.class);
        RuleEngine engine = mock(RuleEngine.class);
        MediaExtractor extractor = mock(MediaExtractor.class);
        AppProperties properties = new AppProperties(
                "langchain",
                512,
                new AppProperties.Crawler(3, 5, 5, new AppProperties.Crawler.WebView(false, true, 25, 8), AppProperties.Crawler.Fallback.disabled()),
                new AppProperties.Llm("", "https://api.deepseek.com/v1", "deepseek-chat", 4),
                new AppProperties.Vision("deepseek-v4-flash-vision-exp", "original", 0.7),
                new AppProperties.Upload("./data/uploads", "http://localhost:8000", 33_554_432),
                new AppProperties.Link(5, 3, 0.65, true)
        );

        PluginRule rule = new PluginRule();
        rule.setName("DM84");
        rule.setUseWebview(true);
        when(registry.usable()).thenReturn(List.of(rule));
        when(registry.resolve("DM84")).thenReturn(rule);

        SearchItem item = new SearchItem("芙莉莲", "http://example.test/detail/1", "");
        when(engine.search(rule, "芙莉莲")).thenReturn(List.of(item));
        when(engine.queryChapters(rule, item.src())).thenReturn(List.of(
                new Road("线路1", List.of("第1集"), List.of("http://example.test/play/1"))
        ));
        when(engine.fetchPageDetailed(rule, "http://example.test/play/1"))
                .thenReturn(new FetchedPage("<html></html>", List.of("https://cdn.example/a.m3u8")));
        when(extractor.extractVideoUrls(any(), eq("http://example.test/play/1"))).thenReturn(List.of());
        when(extractor.extractImageUrls(any(), eq("http://example.test/play/1"))).thenReturn(List.of());
        when(extractor.extractPageLinks(any(), eq("http://example.test/play/1"))).thenReturn(List.of());

        ResourceCrawlerService service = new ResourceCrawlerService(registry, engine, extractor, properties);
        CrawlResourceResult result = service.crawl("芙莉莲", "DM84");
        assertEquals(1, result.videos().size());
        assertEquals("https://cdn.example/a.m3u8", result.videos().get(0).url());
        assertEquals("第1集", result.videos().get(0).title());
    }

    @Test
    void fallsBackToDedicatedSiteWhenPluginHasNoVideos() {
        PluginRegistry registry = mock(PluginRegistry.class);
        RuleEngine engine = mock(RuleEngine.class);
        MediaExtractor extractor = mock(MediaExtractor.class);
        AppProperties properties = new AppProperties(
                "langchain",
                512,
                new AppProperties.Crawler(3, 5, 5, new AppProperties.Crawler.WebView(false, true, 25, 8), AppProperties.Crawler.Fallback.disabled()),
                new AppProperties.Llm("", "https://api.deepseek.com/v1", "deepseek-chat", 4),
                new AppProperties.Vision("deepseek-v4-flash-vision-exp", "original", 0.7),
                new AppProperties.Upload("./data/uploads", "http://localhost:8000", 33_554_432),
                new AppProperties.Link(5, 3, 0.65, true)
        );
        PluginRule rule = new PluginRule();
        rule.setName("DM84");
        when(registry.usable()).thenReturn(List.of(rule));
        when(registry.resolve("DM84")).thenReturn(rule);
        when(engine.search(rule, "芙莉莲")).thenThrow(new AppException(ErrorCode.CRAWL_FAILED, "搜索失败: HTTP 522"));

        SiteFallback yhdm = mock(SiteFallback.class);
        when(yhdm.name()).thenReturn("YHDM");
        when(yhdm.enabled()).thenReturn(true);
        when(yhdm.matches(any())).thenReturn(false);
        when(yhdm.crawl("芙莉莲", 3, 5)).thenReturn(new CrawlResourceResult(
                "芙莉莲",
                "YHDM",
                "YHDM",
                List.of(new CrawlResourceResult.VideoResource("第1集", "https://cdn.example/a.m3u8", "http://y/v/1", "默认播放列表")),
                List.of(),
                List.of()
        ));

        ResourceCrawlerService service = new ResourceCrawlerService(
                registry, engine, extractor, properties, List.of(yhdm)
        );
        CrawlResourceResult result = service.crawl("芙莉莲", "DM84");
        assertEquals("YHDM", result.pluginName());
        assertEquals("https://cdn.example/a.m3u8", result.videos().get(0).url());
    }

    @Test
    void skipsOpenPluginCircuitAndUsesFallback() {
        PluginRegistry registry = mock(PluginRegistry.class);
        RuleEngine engine = mock(RuleEngine.class);
        MediaExtractor extractor = mock(MediaExtractor.class);
        AppProperties properties = testProperties();
        PluginRule rule = new PluginRule();
        rule.setName("DM84");
        when(registry.usable()).thenReturn(List.of(rule));
        when(registry.resolve("DM84")).thenReturn(rule);

        SiteCircuitBoard board = new SiteCircuitBoard(CrawlerReliabilityProperties.defaults());
        for (int i = 0; i < 5; i++) {
            board.recordFailure(UpstreamKeys.plugin("DM84"));
        }

        SiteFallback yhdm = mock(SiteFallback.class);
        when(yhdm.name()).thenReturn("YHDM");
        when(yhdm.enabled()).thenReturn(true);
        when(yhdm.matches(any())).thenReturn(false);
        when(yhdm.crawl("芙莉莲", 3, 5)).thenReturn(new CrawlResourceResult(
                "芙莉莲",
                "YHDM",
                "YHDM",
                List.of(new CrawlResourceResult.VideoResource("第1集", "https://cdn.example/a.m3u8", "http://y/v/1", "默认播放列表")),
                List.of(),
                List.of()
        ));

        ResourceCrawlerService service = new ResourceCrawlerService(
                registry, engine, extractor, properties, List.of(yhdm), board, CrawlSingleflight.direct()
        );
        CrawlResourceResult result = service.crawl("芙莉莲", "DM84");
        assertEquals("YHDM", result.pluginName());
        org.mockito.Mockito.verify(engine, org.mockito.Mockito.never()).search(any(), any());
    }

    @Test
    void titleMissDoesNotOpenPluginCircuit() {
        PluginRegistry registry = mock(PluginRegistry.class);
        RuleEngine engine = mock(RuleEngine.class);
        MediaExtractor extractor = mock(MediaExtractor.class);
        AppProperties properties = testProperties();
        PluginRule rule = new PluginRule();
        rule.setName("DM84");
        when(registry.usable()).thenReturn(List.of(rule));
        when(registry.resolve("DM84")).thenReturn(rule);
        when(engine.search(rule, "不存在的番")).thenThrow(
                new AppException(ErrorCode.CRAWL_FAILED, "站点 DM84 未找到匹配结果")
        );

        SiteCircuitBoard board = new SiteCircuitBoard(CrawlerReliabilityProperties.defaults());
        ResourceCrawlerService service = new ResourceCrawlerService(
                registry, engine, extractor, properties, List.of(), board, CrawlSingleflight.direct()
        );
        for (int i = 0; i < 8; i++) {
            service.crawl("不存在的番", "DM84");
        }
        assertFalse(board.isOpen(UpstreamKeys.plugin("DM84")));
    }

    @Test
    void secondCrawlHitsPlayCacheAndSkipsEngine() {
        PluginRegistry registry = mock(PluginRegistry.class);
        RuleEngine engine = mock(RuleEngine.class);
        MediaExtractor extractor = mock(MediaExtractor.class);
        AppProperties properties = testProperties();
        PluginRule rule = new PluginRule();
        rule.setName("DM84");
        when(registry.usable()).thenReturn(List.of(rule));
        when(registry.resolve("DM84")).thenReturn(rule);

        SearchItem item = new SearchItem("芙莉莲", "http://example.test/detail/1", "");
        when(engine.search(rule, "芙莉莲")).thenReturn(List.of(item));
        when(engine.queryChapters(rule, item.src())).thenReturn(List.of(
                new Road("线路1", List.of("第1集"), List.of("http://example.test/play/1"))
        ));
        when(engine.fetchPageDetailed(rule, "http://example.test/play/1"))
                .thenReturn(new FetchedPage("<html></html>", List.of("https://cdn.example/a.m3u8")));
        when(extractor.extractVideoUrls(any(), eq("http://example.test/play/1"))).thenReturn(List.of());
        when(extractor.extractImageUrls(any(), eq("http://example.test/play/1"))).thenReturn(List.of());
        when(extractor.extractPageLinks(any(), eq("http://example.test/play/1"))).thenReturn(List.of());

        CaffeineCrawlerResultCache cache = new CaffeineCrawlerResultCache(CrawlerCacheProperties.caffeineOnly());
        ResourceCrawlerService service = new ResourceCrawlerService(
                registry,
                engine,
                extractor,
                properties,
                List.of(),
                SiteCircuitBoard.disabled(),
                CrawlSingleflight.direct(),
                cache
        );
        CrawlResourceResult first = service.crawl("芙莉莲", "DM84");
        CrawlResourceResult second = service.crawl("芙莉莲", "DM84");
        assertEquals(first.videos().get(0).url(), second.videos().get(0).url());
        org.mockito.Mockito.verify(engine, org.mockito.Mockito.times(1)).search(rule, "芙莉莲");
        assertThat(cache.getCatalog(CrawlCacheKeys.lookup("DM84", "芙莉莲"))).isNotNull();
        assertThat(cache.getCatalog(CrawlCacheKeys.lookup("DM84", "芙莉莲")).toString())
                .doesNotContain("cdn.example/a.m3u8");
    }

    @Test
    void failedCrawlIsNotCached() {
        PluginRegistry registry = mock(PluginRegistry.class);
        RuleEngine engine = mock(RuleEngine.class);
        MediaExtractor extractor = mock(MediaExtractor.class);
        PluginRule rule = new PluginRule();
        rule.setName("DM84");
        when(registry.usable()).thenReturn(List.of(rule));
        when(registry.resolve("DM84")).thenReturn(rule);
        when(engine.search(rule, "没有这部")).thenThrow(
                new AppException(ErrorCode.CRAWL_FAILED, "站点 DM84 未找到匹配结果")
        );

        CaffeineCrawlerResultCache cache = new CaffeineCrawlerResultCache(CrawlerCacheProperties.caffeineOnly());
        ResourceCrawlerService service = new ResourceCrawlerService(
                registry,
                engine,
                extractor,
                testProperties(),
                List.of(),
                SiteCircuitBoard.disabled(),
                CrawlSingleflight.direct(),
                cache
        );
        service.crawl("没有这部", "DM84");
        assertThat(cache.getPlay(CrawlCacheKeys.lookup("DM84", "没有这部"))).isNull();
        service.crawl("没有这部", "DM84");
        org.mockito.Mockito.verify(engine, org.mockito.Mockito.times(2)).search(rule, "没有这部");
    }

    private static AppProperties testProperties() {
        return new AppProperties(
                "langchain",
                512,
                new AppProperties.Crawler(3, 5, 5, new AppProperties.Crawler.WebView(false, true, 25, 8), AppProperties.Crawler.Fallback.disabled()),
                new AppProperties.Llm("", "https://api.deepseek.com/v1", "deepseek-chat", 4),
                new AppProperties.Vision("deepseek-v4-flash-vision-exp", "original", 0.7),
                new AppProperties.Upload("./data/uploads", "http://localhost:8000", 33_554_432),
                new AppProperties.Link(5, 3, 0.65, true)
        );
    }
}
