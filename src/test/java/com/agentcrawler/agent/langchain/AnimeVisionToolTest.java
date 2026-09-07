package com.agentcrawler.agent.langchain;

import com.agentcrawler.agent.session.SessionBlackboardService;
import com.agentcrawler.agent.session.SessionBlackboardStore;
import com.agentcrawler.agent.session.SessionContextHolder;
import com.agentcrawler.config.AppProperties;
import com.agentcrawler.vision.AnimeVisionAnalysisResult;
import com.agentcrawler.vision.DeepSeekVisionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnimeVisionToolTest {

    @Mock
    private DeepSeekVisionClient visionClient;

    private SessionBlackboardService blackboardService;
    private AnimeVisionTool visionTool;

    @BeforeEach
    void setUp() {
        blackboardService = new SessionBlackboardService(new SessionBlackboardStore());
        AppProperties properties = new AppProperties(
                "langchain",
                512,
                new AppProperties.Crawler(3, 5, 5),
                new AppProperties.Llm("key", "https://api.deepseek.com/v1", "deepseek-chat", 4),
                new AppProperties.Vision("deepseek-v4-flash-vision-exp", "original", 0.7),
                new AppProperties.Upload("./data/uploads", "http://localhost:8000", 33_554_432)
        );
        visionTool = new AnimeVisionTool(visionClient, blackboardService, properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SessionContextHolder.clear();
    }

    @Test
    void autoLocksBlackboardAfterVisionAnalysis() throws Exception {
        AnimeVisionAnalysisResult result = new AnimeVisionAnalysisResult();
        result.setWorkTitle("鬼灭之刃 游郭篇");
        result.setCharacters(java.util.List.of("灶门炭治郎"));
        result.setEpisodeHint("第6集");
        result.setSearchScene("开斑纹对决");
        result.setVisualFeatures(java.util.List.of("绿色羽织"));
        result.setConfidence(0.95);

        when(visionClient.analyze(anyString())).thenReturn(result);

        SessionContextHolder.set("session-1");
        String json = visionTool.analyzeAnimeImage("http://localhost:8000/api/v1/files/images/img_demo");

        assertThat(json).contains("鬼灭之刃 游郭篇");
        assertThat(blackboardService.get("session-1").getWorkTitle()).isEqualTo("鬼灭之刃 游郭篇");
        assertThat(blackboardService.get("session-1").isLocked()).isTrue();
    }
}
