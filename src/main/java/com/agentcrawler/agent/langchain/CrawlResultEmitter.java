package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.streaming.StreamEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CrawlResultEmitter {
    private CrawlResultEmitter() {}

    public static void emitFromJson(String rawResult, StreamEmitter emitter, ObjectMapper objectMapper) {
        if (rawResult == null || rawResult.isBlank()) {
            return;
        }
        try {
            CrawlResourceResult result = objectMapper.readValue(rawResult, CrawlResourceResult.class);
            emit(result, emitter, false);
        } catch (Exception ex) {
            // 工具失败或摘要 JSON 无法还原全量结果时，不要把异常原文写进对话流
        }
    }

    public static void emit(CrawlResourceResult result, StreamEmitter emitter) {
        emit(result, emitter, false);
    }

    public static void emit(CrawlResourceResult result, StreamEmitter emitter, boolean speakEmpty) {
        if (result == null) {
            return;
        }
        if (!result.hasResources()) {
            if (speakEmpty) {
                String message = result.error() == null || result.error().isBlank()
                        ? "未提取到可用资源，请更换关键词或站点后重试。"
                        : result.error();
                emitter.text(message);
            }
            return;
        }

        ResourceRouteGrouper.GroupedResources grouped = ResourceRouteGrouper.group(result);
        if (!grouped.routes().isEmpty()) {
            emitter.resourceBundle(toBundlePayload(result, grouped));
            for (ResourceRouteGrouper.Route route : grouped.routes()) {
                for (ResourceRouteGrouper.Episode episode : route.episodes()) {
                    emitter.video(
                            episode.url(),
                            episode.title(),
                            episode.format(),
                            episode.sourcePage(),
                            route.name()
                    );
                }
            }
        }

        if (result.links() != null) {
            for (CrawlResourceResult.LinkResource link : result.links()) {
                emitter.link(link.url(), link.title(), link.description());
            }
        }

        if (result.images() != null) {
            for (CrawlResourceResult.ImageResource image : result.images()) {
                emitter.image(image.url(), image.alt());
            }
        }
    }

    static Map<String, Object> toBundlePayload(
            CrawlResourceResult result,
            ResourceRouteGrouper.GroupedResources grouped
    ) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (ResourceRouteGrouper.Route route : grouped.routes()) {
            List<Map<String, Object>> episodes = new ArrayList<>();
            for (ResourceRouteGrouper.Episode episode : route.episodes()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", episode.title());
                item.put("url", episode.url());
                item.put("format", episode.format());
                episodes.add(item);
            }
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("source_name", route.name());
            source.put("kind", route.kind());
            source.put("recommended", route.recommended());
            source.put("episodes", episodes);
            sources.add(source);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("anime_title", grouped.title() == null || grouped.title().isBlank()
                ? result.keyword()
                : grouped.title());
        if (result.pluginName() != null && !result.pluginName().isBlank()) {
            payload.put("plugin_name", result.pluginName());
        }
        payload.put("current_episodes_count", grouped.uniqueEpisodeCount());
        payload.put("sources", sources);
        return payload;
    }
}
