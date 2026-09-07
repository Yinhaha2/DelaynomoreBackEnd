package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.streaming.StreamEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class CrawlResultEmitter {
    private CrawlResultEmitter() {}

    public static void emitFromJson(String rawResult, StreamEmitter emitter, ObjectMapper objectMapper) {
        try {
            CrawlResourceResult result = objectMapper.readValue(rawResult, CrawlResourceResult.class);
            emit(result, emitter);
        } catch (Exception ex) {
            emitter.text(rawResult);
        }
    }

    public static void emit(CrawlResourceResult result, StreamEmitter emitter) {
        emitter.text("插件: " + result.pluginName() + "\n");
        emitter.text("关键词: " + result.keyword() + "\n\n");

        if (!result.videos().isEmpty()) {
            emitter.text("视频资源（" + result.videos().size() + "）:\n");
            for (CrawlResourceResult.VideoResource video : result.videos()) {
                emitter.video(
                        video.url(),
                        video.title(),
                        detectVideoFormat(video.url()),
                        video.sourcePage(),
                        video.roadName()
                );
                emitter.text("- " + video.title() + " => " + video.url() + "\n");
            }
            emitter.text("\n");
        }

        if (!result.links().isEmpty()) {
            emitter.text("相关链接（" + result.links().size() + "）:\n");
            for (CrawlResourceResult.LinkResource link : result.links()) {
                emitter.link(link.url(), link.title(), link.description());
            }
            emitter.text("\n");
        }

        if (!result.images().isEmpty()) {
            emitter.text("图片资源（" + result.images().size() + "）:\n");
            for (CrawlResourceResult.ImageResource image : result.images()) {
                emitter.image(image.url(), image.alt());
            }
        }

        if (result.videos().isEmpty() && result.links().isEmpty() && result.images().isEmpty()) {
            emitter.text("未提取到可用资源，请更换关键词或站点后重试。");
        }
    }

    private static String detectVideoFormat(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".m3u8")) {
            return "m3u8";
        }
        if (lower.contains(".mp4")) {
            return "mp4";
        }
        return "unknown";
    }
}
