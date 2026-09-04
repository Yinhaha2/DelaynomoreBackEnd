package com.agentcrawler.crawler.model;

import java.util.List;

public record CrawlResourceResult(
        String keyword,
        String site,
        String pluginName,
        List<VideoResource> videos,
        List<LinkResource> links,
        List<ImageResource> images
) {
    public record VideoResource(String title, String url, String sourcePage, String roadName) {}

    public record LinkResource(String title, String url, String description) {}

    public record ImageResource(String url, String alt) {}
}
