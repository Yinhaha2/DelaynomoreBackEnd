package com.agentcrawler.crawler.model;

import java.util.List;

public record CrawlResourceResult(
        String keyword,
        String site,
        String pluginName,
        List<VideoResource> videos,
        List<LinkResource> links,
        List<ImageResource> images,
        String error
) {
    public CrawlResourceResult(
            String keyword,
            String site,
            String pluginName,
            List<VideoResource> videos,
            List<LinkResource> links,
            List<ImageResource> images
    ) {
        this(keyword, site, pluginName, videos, links, images, null);
    }

    public static CrawlResourceResult failed(String keyword, String site, String pluginName, String error) {
        return new CrawlResourceResult(
                keyword,
                site,
                pluginName,
                List.of(),
                List.of(),
                List.of(),
                error
        );
    }

    public boolean hasResources() {
        return (videos != null && !videos.isEmpty())
                || (links != null && !links.isEmpty())
                || (images != null && !images.isEmpty());
    }

    public record VideoResource(String title, String url, String sourcePage, String roadName) {}

    public record LinkResource(String title, String url, String description) {}

    public record ImageResource(String url, String alt) {}
}
