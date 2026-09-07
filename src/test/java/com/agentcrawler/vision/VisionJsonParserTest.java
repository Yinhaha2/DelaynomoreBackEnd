package com.agentcrawler.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisionJsonParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesJsonWrappedInMarkdownFence() {
        String raw = """
                ```json
                {
                  "work_title": "鬼灭之刃 游郭篇",
                  "characters": ["灶门炭治郎"],
                  "episode_hint": "第6集",
                  "search_scene": "对决",
                  "visual_features": ["羽织"],
                  "confidence": 0.92
                }
                ```
                """;

        AnimeVisionAnalysisResult result = VisionJsonParser.parse(objectMapper, raw);

        assertThat(result.getWorkTitle()).isEqualTo("鬼灭之刃 游郭篇");
        assertThat(result.getCharacters()).containsExactly("灶门炭治郎");
        assertThat(result.getConfidence()).isEqualTo(0.92);
    }
}
