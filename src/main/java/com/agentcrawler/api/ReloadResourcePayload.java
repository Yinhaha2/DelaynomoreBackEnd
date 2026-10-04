package com.agentcrawler.api;

import com.agentcrawler.agent.langchain.CrawlResultEmitter;
import com.agentcrawler.agent.langchain.ResourceRouteGrouper;
import com.agentcrawler.crawler.model.CrawlResourceResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReloadResourcePayload {
    private ReloadResourcePayload() {}

    static Map<String, Object> from(CrawlResourceResult result) {
        boolean refreshed = result != null && result.videos() != null && !result.videos().isEmpty();
        String keyword = result == null || result.keyword() == null ? "" : result.keyword();
        String site = result == null || result.site() == null ? "" : result.site();
        String message = refreshed
                ? null
                : (result == null || result.error() == null || result.error().isBlank()
                ? "暂时没有找到可用资源，请稍后再试。"
                : result.error());
        return body(keyword, site, refreshed, message, result);
    }

    static Map<String, Object> failed(String keyword, String site, String message) {
        return body(keyword, site, false, message, null);
    }

    private static Map<String, Object> body(
            String keyword,
            String site,
            boolean refreshed,
            String message,
            CrawlResourceResult result
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyword", keyword == null ? "" : keyword);
        body.put("site", site == null ? "" : site);
        body.put("refreshed", refreshed);
        if (message != null && !message.isBlank()) {
            body.put("message", message);
        }
        if (result == null) {
            return body;
        }
        ResourceRouteGrouper.GroupedResources grouped = ResourceRouteGrouper.group(result);
        if (!grouped.routes().isEmpty()) {
            Map<String, Object> bundle = new LinkedHashMap<>(CrawlResultEmitter.toBundlePayload(result, grouped));
            bundle.put("type", "resource_bundle");
            body.put("resource_bundle", bundle);
        }
        body.put("links", links(result));
        body.put("images", images(result));
        return body;
    }

    private static List<Map<String, Object>> links(CrawlResourceResult result) {
        List<Map<String, Object>> links = new ArrayList<>();
        if (result.links() == null) {
            return links;
        }
        for (CrawlResourceResult.LinkResource link : result.links()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "link");
            item.put("url", link.url());
            item.put("title", link.title());
            item.put("description", link.description());
            links.add(item);
        }
        return links;
    }

    private static List<Map<String, Object>> images(CrawlResourceResult result) {
        List<Map<String, Object>> images = new ArrayList<>();
        if (result.images() == null) {
            return images;
        }
        for (CrawlResourceResult.ImageResource image : result.images()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "image");
            item.put("url", image.url());
            item.put("alt", image.alt());
            images.add(item);
        }
        return images;
    }
}
