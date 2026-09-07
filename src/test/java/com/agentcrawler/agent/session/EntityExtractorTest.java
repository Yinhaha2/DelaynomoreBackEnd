package com.agentcrawler.agent.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityExtractorTest {

    @Test
    void extractsQuotedWorkTitle() {
        assertThat(EntityExtractor.extractWorkTitle("这是《鬼灭之刃》的表情包"))
                .contains("鬼灭之刃");
    }

    @Test
    void extractsEpisodeHintWithTimestamp() {
        assertThat(EntityExtractor.extractEpisodeHint("大概在第8集 12分30秒"))
                .isEqualTo("第8集 12分30秒");
    }

    @Test
    void parsesCharacterList() {
        assertThat(EntityExtractor.parseCharacterList("灶门炭治郎、堕姬"))
                .containsExactly("灶门炭治郎", "堕姬");
    }
}
