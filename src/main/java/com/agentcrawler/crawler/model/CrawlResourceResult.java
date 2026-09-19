package com.agentcrawler.crawler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoResource(String title, String url, String sourcePage, String roadName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinkResource(String title, String url, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageResource(String url, String alt) {}
}
