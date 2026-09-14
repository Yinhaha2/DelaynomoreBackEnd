package com.agentcrawler.crawler.cache;

import com.agentcrawler.agent.langchain.ResourceRouteGrouper;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Long-lived catalog: titles and routes only. Never stores signed play URLs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogSnapshot(
        String keyword,
        String site,
        String pluginName,
        int episodeCount,
        String latestEpisode,
        List<Source> sources
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            String sourceName,
            String kind,
            boolean recommended,
            List<Episode> episodes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Episode(String title, String format, String sourcePage) {}

    public static CatalogSnapshot from(CrawlResourceResult result) {
        if (result == null) {
            return new CatalogSnapshot("", "", "", 0, null, List.of());
        }
        ResourceRouteGrouper.GroupedResources grouped = ResourceRouteGrouper.group(result);
        List<Source> sources = new ArrayList<>();
        for (ResourceRouteGrouper.Route route : grouped.routes()) {
            List<Episode> episodes = new ArrayList<>();
            for (ResourceRouteGrouper.Episode episode : route.episodes()) {
                episodes.add(new Episode(episode.title(), episode.format(), episode.sourcePage()));
            }
            sources.add(new Source(route.name(), route.kind(), route.recommended(), List.copyOf(episodes)));
        }
        return new CatalogSnapshot(
                result.keyword(),
                result.site(),
                result.pluginName(),
                grouped.uniqueEpisodeCount(),
                grouped.latestEpisode(),
                List.copyOf(sources)
        );
    }
}
