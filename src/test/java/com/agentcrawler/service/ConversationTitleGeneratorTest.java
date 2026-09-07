package com.agentcrawler.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTitleGeneratorTest {

    @Test
    void usesQuotedWorkAndPlayIntent() {
        String title = ConversationTitleGenerator.generate(
                "帮我找《葬送的芙莉莲》的播放资源",
                false,
                null
        );
        assertThat(title).isEqualTo("葬送的芙莉莲播放资源");
    }

    @Test
    void prefersLockedWorkTitle() {
        String title = ConversationTitleGenerator.generate(
                "这张表情包出自哪一集？",
                true,
                "鬼灭之刃 游郭篇"
        );
        assertThat(title).isEqualTo("鬼灭之刃 游郭篇出处");
    }

    @Test
    void imageOnlyUsesDefaultTitle() {
        String title = ConversationTitleGenerator.generate(
                "请分析这张图片并告诉我出处。",
                true,
                null
        );
        assertThat(title).isEqualTo("图片识图");
    }

    @Test
    void clipsToTwentyFourCodePoints() {
        String title = ConversationTitleGenerator.generate(
                "帮我找《这是一个非常非常非常非常非常长的番剧名字超过限制》",
                false,
                null
        );
        assertThat(title.codePointCount(0, title.length())).isEqualTo(24);
    }
}
