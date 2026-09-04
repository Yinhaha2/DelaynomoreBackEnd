package com.agentcrawler.crawler.engine;

import com.agentcrawler.crawler.http.SiteHttpClient;
import com.agentcrawler.crawler.model.ApiChapterConfig;
import com.agentcrawler.crawler.model.ApiRequestConfig;
import com.agentcrawler.crawler.model.ApiSearchConfig;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRuleStrategyTest {
    private MockWebServer server;
    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        ruleEngine = new RuleEngine(new SiteHttpClient(5), new XPathRuleStrategy(), new ApiRuleStrategy());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void searchAndQueryChapters_withNestedApiRule() {
        server.enqueue(new MockResponse().setBody(searchJson()));
        server.enqueue(new MockResponse().setBody(chapterJson()));

        PluginRule rule = apiPlugin(server.url("/").toString());
        List<SearchItem> items = ruleEngine.search(rule, "葬送的芙莉莲");
        assertEquals(1, items.size());
        assertEquals("葬送的芙莉莲", items.get(0).name());
        assertTrue(items.get(0).src().endsWith("/1001"));
        assertTrue(items.get(0).imageUrl().contains("/poster.jpg"));

        List<Road> roads = ruleEngine.queryChapters(rule, items.get(0).src());
        assertEquals(1, roads.size());
        assertEquals("主线", roads.get(0).name());
        assertEquals("第1集", roads.get(0).episodeNames().get(0));
        assertTrue(roads.get(0).episodeUrls().get(0).contains("/play/1.m3u8"));
    }

    @Test
    void queryChapters_withDelimitedApiRule() {
        server.enqueue(new MockResponse().setBody(searchJson()));
        server.enqueue(new MockResponse().setBody(delimitedChapterJson()));

        PluginRule rule = delimitedApiPlugin(server.url("/").toString());
        List<SearchItem> items = ruleEngine.search(rule, "间谍过家家");
        List<Road> roads = ruleEngine.queryChapters(rule, items.get(0).src());
        assertEquals(1, roads.size());
        assertEquals("默认线路", roads.get(0).name());
        assertEquals(2, roads.get(0).episodeUrls().size());
    }

    private static PluginRule apiPlugin(String baseUrl) {
        PluginRule rule = new PluginRule();
        rule.setName("MockApi");
        rule.setBaseURL(baseUrl);
        rule.setSearchMode("api");
        rule.setChapterMode("api");

        ApiSearchConfig searchConfig = new ApiSearchConfig();
        ApiRequestConfig searchRequest = new ApiRequestConfig();
        searchRequest.setMethod("GET");
        searchRequest.setUrl(baseUrl + "api/search");
        searchRequest.getQuery().put("q", "@keyword");
        searchConfig.setRequest(searchRequest);
        searchConfig.setListPath("$.data[*]");
        searchConfig.setNamePath("$.title");
        searchConfig.setSourcePath("$.id");
        searchConfig.setImagePath("$.cover");
        rule.setSearchApiConfig(searchConfig);

        ApiChapterConfig chapterConfig = new ApiChapterConfig();
        ApiRequestConfig chapterRequest = new ApiRequestConfig();
        chapterRequest.setMethod("GET");
        chapterRequest.setUrl(baseUrl + "api/detail/@source");
        chapterConfig.setRequest(chapterRequest);
        chapterConfig.setRoadsPath("$.data.roads[*]");
        chapterConfig.setRoadNamePath("$.name");
        chapterConfig.setEpisodesPath("$.episodes[*]");
        chapterConfig.setEpisodeNamePath("$.title");
        chapterConfig.setEpisodeUrlPath("$.url");
        rule.setChapterApiConfig(chapterConfig);
        return rule;
    }

    private static PluginRule delimitedApiPlugin(String baseUrl) {
        PluginRule rule = apiPlugin(baseUrl);
        rule.setName("MockApiDelimited");
        ApiChapterConfig chapterConfig = rule.getChapterApiConfig();
        chapterConfig.setFormat("delimited");
        chapterConfig.setRoadNamesPath("$.data.road_names");
        chapterConfig.setRoadEpisodesPath("$.data.road_episodes");
        return rule;
    }

    private static String searchJson() {
        return """
                {
                  "data": [
                    {
                      "title": "葬送的芙莉莲",
                      "id": "1001",
                      "cover": "/poster.jpg"
                    }
                  ]
                }
                """;
    }

    private static String chapterJson() {
        return """
                {
                  "data": {
                    "roads": [
                      {
                        "name": "主线",
                        "episodes": [
                          {"title": "第1集", "url": "/play/1.m3u8"}
                        ]
                      }
                    ]
                  }
                }
                """;
    }

    private static String delimitedChapterJson() {
        return """
                {
                  "data": {
                    "road_names": "默认线路",
                    "road_episodes": "第1集$/ep/1.m3u8#第2集$/ep/2.m3u8"
                  }
                }
                """;
    }
}
