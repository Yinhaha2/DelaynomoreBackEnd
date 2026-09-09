package com.agentcrawler.streaming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseEncoderTest {

    @Test
    void doneIncludesTitleWhenProvided() {
        String frame = SseEncoder.done("msg_1", "conv_1", "葬送的芙莉莲播放资源");
        assertThat(frame).contains("event: done");
        assertThat(frame).contains("\"title\":\"葬送的芙莉莲播放资源\"");
    }

    @Test
    void doneOmitsTitleWhenBlank() {
        String frame = SseEncoder.done("msg_1", "conv_1", null);
        assertThat(frame).doesNotContain("\"title\"");
    }

    @Test
    void resourceBundleUsesChunkEventAndSnakeCaseFields() {
        String frame = SseEncoder.resourceBundle(java.util.Map.of(
                "anime_title", "鬼灭之刃",
                "plugin_name", "SiliSili",
                "current_episodes_count", 5,
                "sources", java.util.List.of()
        ));
        assertThat(frame).startsWith("event: chunk\n");
        assertThat(frame).contains("\"type\":\"resource_bundle\"");
        assertThat(frame).contains("\"anime_title\":\"鬼灭之刃\"");
        assertThat(frame).contains("\"current_episodes_count\":5");
        assertThat(frame).doesNotContain("\"type\":\"type\"");
    }
}
