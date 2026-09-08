package com.agentcrawler.link;

import com.agentcrawler.agent.session.SessionBlackboardService;
import com.agentcrawler.agent.session.SessionBlackboardStore;
import com.agentcrawler.config.AppProperties;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkInspectorServiceTest {

    private MockWebServer server;
    private SessionBlackboardService blackboard;
    private LinkInspectorService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        blackboard = new SessionBlackboardService(new SessionBlackboardStore());
        AppProperties properties = new AppProperties(
                "langchain",
                512,
                new AppProperties.Crawler(3, 5, 5, new AppProperties.Crawler.WebView(false, true, 25, 8), AppProperties.Crawler.Fallback.disabled()),
                new AppProperties.Llm("", "https://api.deepseek.com/v1", "deepseek-chat", 4),
                new AppProperties.Vision("deepseek-v4-flash-vision-exp", "original", 0.7),
                new AppProperties.Upload("./data/uploads", "http://localhost:8000", 33_554_432),
                new AppProperties.Link(3, 3, 0.65, true)
        );
        service = new LinkInspectorService(new LinkHttpClient(3), blackboard, properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void inspectsHtmlAndLocksBlackboard() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "text/html; charset=utf-8")
                        .setBody("""
                                <html><head>
                                <meta property="og:title" content="《鬼灭之刃 游郭篇》第6集">
                                <title>ignore</title>
                                </head></html>
                                """);
            }
        });

        String url = server.url("/anime/6").toString();
        List<LinkInspectionResult> results = service.inspectMessage(
                "s-link",
                "帮我看看这个 " + url + " 是啥"
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getWorkTitle()).isEqualTo("鬼灭之刃 游郭篇");
        assertThat(blackboard.get("s-link").isLocked()).isTrue();
        assertThat(blackboard.get("s-link").getWorkTitle()).isEqualTo("鬼灭之刃 游郭篇");
    }

    @Test
    void parsesMagnetWithoutHttp() {
        LinkInspectionResult result = service.inspect(
                "magnet:?xt=urn:btih:abc123def456&dn=%E9%AC%BC%E7%81%AD%E4%B9%8B%E5%88%83"
        );

        assertThat(result.getKind()).isEqualTo(LinkKind.MAGNET);
        assertThat(result.getInfoHash()).isEqualTo("abc123def456");
        assertThat(result.getDisplayName()).isEqualTo("鬼灭之刃");
    }
}
