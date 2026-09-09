package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.streaming.StreamEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlResultEmitterTest {

    @Test
    void emitSendsResourceBundleWithoutDumpingUrlsAsText() {
        List<String> frames = new ArrayList<>();
        StreamEmitter emitter = new StreamEmitter(512, frames::add);
        CrawlResourceResult result = new CrawlResourceResult(
                "鬼灭之刃",
                "SiliSili",
                "SiliSili",
                List.of(
                        new CrawlResourceResult.VideoResource(
                                "第01集",
                                "https://silivideo.example.r2.cloudflarestorage.com/ep1.mp4?X-Amz-Signature=secret",
                                "https://example.com/play/1",
                                null
                        ),
                        new CrawlResourceResult.VideoResource(
                                "第01集",
                                "https://svipsvip.ffzy-online5.com/ep1/index.m3u8",
                                "https://example.com/play/1",
                                null
                        )
                ),
                List.of(new CrawlResourceResult.LinkResource("详情", "https://example.com/detail", "搜索结果")),
                List.of(new CrawlResourceResult.ImageResource("https://cdn.example.com/poster.jpg", "鬼灭之刃"))
        );

        CrawlResultEmitter.emit(result, emitter);

        String all = String.join("", frames);
        assertThat(all).contains("\"type\":\"resource_bundle\"");
        assertThat(all).contains("\"anime_title\":\"鬼灭之刃\"");
        assertThat(all).contains("\"source_name\":\"高速直链 (R2)\"");
        assertThat(all).contains("\"type\":\"video\"");
        assertThat(all).contains("\"type\":\"link\"");
        assertThat(all).contains("\"type\":\"image\"");
        assertThat(emitter.collectedText()).doesNotContain("https://");
        assertThat(emitter.collectedText()).doesNotContain("插件:");
        assertThat(all).doesNotContain("第01集 =>");
    }

    @Test
    void toolSummaryOmitsPlaybackUrls() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        CrawlResourceResult result = new CrawlResourceResult(
                "鬼灭之刃",
                "SiliSili",
                "SiliSili",
                List.of(new CrawlResourceResult.VideoResource(
                        "第01集",
                        "https://silivideo.example.r2.cloudflarestorage.com/ep1.mp4?X-Amz-Signature=secret",
                        "https://example.com/play/1",
                        null
                )),
                List.of(),
                List.of()
        );

        String json = mapper.writeValueAsString(CrawlToolSummary.from(result));

        assertThat(json).contains("\"ok\":true");
        assertThat(json).contains("\"title\":\"鬼灭之刃\"");
        assertThat(json).contains("\"route_count\":1");
        assertThat(json).contains("高速直链 (R2)");
        assertThat(json).doesNotContain("https://");
        assertThat(json).doesNotContain("X-Amz-Signature");
    }
}
