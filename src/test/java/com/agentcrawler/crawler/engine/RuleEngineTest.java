package com.agentcrawler.crawler.engine;

import com.agentcrawler.crawler.http.SiteHttpClient;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {
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
    void searchAndQueryChapters_withKazumiLikeHtml() {
        server.enqueue(new MockResponse().setBody(searchHtml()));
        server.enqueue(new MockResponse().setBody(chapterHtml()));

        PluginRule rule = pluginRule(server.url("/").toString());
        List<SearchItem> items = ruleEngine.search(rule, "葬送的芙莉莲");
        assertEquals(1, items.size());
        assertEquals("葬送的芙莉莲", items.get(0).name());
        assertTrue(items.get(0).src().contains("/detail/1"));
        assertTrue(items.get(0).imageUrl().contains("/poster.jpg"));

        List<Road> roads = ruleEngine.queryChapters(rule, items.get(0).src());
        assertEquals(1, roads.size());
        assertEquals("线路1", roads.get(0).name());
        assertEquals("第1集", roads.get(0).episodeNames().get(0));
        assertTrue(roads.get(0).episodeUrls().get(0).contains("/play/1.m3u8"));
    }

    private static PluginRule pluginRule(String baseUrl) {
        PluginRule rule = new PluginRule();
        rule.setName("mock");
        rule.setBaseURL(baseUrl);
        rule.setSearchURL(baseUrl + "search?wd=@keyword");
        rule.setSearchList("//div/div[3]/ul/li");
        rule.setSearchName("//div/a[2]");
        rule.setSearchResult("//div/a[2]");
        rule.setSearchImage("//div/a[1]/img");
        rule.setChapterRoads("//div/div[4]/div/ul");
        rule.setChapterResult("//li/a");
        return rule;
    }

    private static String searchHtml() {
        return """
                <div>
                  <div></div>
                  <div></div>
                  <div>
                    <ul>
                      <li>
                        <div>
                          <a><img src="/poster.jpg"></a>
                          <a href="/detail/1">葬送的芙莉莲</a>
                        </div>
                      </li>
                    </ul>
                  </div>
                </div>
                """;
    }

    private static String chapterHtml() {
        return """
                <div>
                  <div></div>
                  <div></div>
                  <div></div>
                  <div>
                    <div>
                      <ul>
                        <li><a href="/play/1.m3u8">第1集</a></li>
                      </ul>
                    </div>
                  </div>
                </div>
                """;
    }
}
