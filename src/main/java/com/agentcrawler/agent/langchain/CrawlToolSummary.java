package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 交给大模型的爬虫摘要：只有线路名与集数，不含任何播放 URL。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CrawlToolSummary(
        boolean ok,
        String title,
        String plugin,
        int routeCount,
        List<String> routeNames,
        int episodeCount,
        String latestEpisode,
        boolean hasDirectMp4,
        boolean hasHls,
        String error
) {
    public static CrawlToolSummary from(CrawlResourceResult result) {
        if (result == null) {
            return new CrawlToolSummary(false, "", "", 0, List.of(), 0, null, false, false, "检索结果为空");
        }
        ResourceRouteGrouper.GroupedResources grouped = ResourceRouteGrouper.group(result);
        List<String> names = grouped.routes().stream()
                .map(ResourceRouteGrouper.Route::name)
                .toList();
        boolean hasMp4 = grouped.routes().stream().anyMatch(route -> "mp4".equals(route.kind()));
        boolean hasHls = grouped.routes().stream().anyMatch(route -> "hls".equals(route.kind()));
        boolean ok = result.hasResources() && (result.error() == null || result.error().isBlank());
        String error = result.error() == null || result.error().isBlank() ? null : result.error();
        return new CrawlToolSummary(
                ok,
                result.keyword(),
                result.pluginName(),
                grouped.routes().size(),
                names,
                grouped.uniqueEpisodeCount(),
                grouped.latestEpisode(),
                hasMp4,
                hasHls,
                error
        );
    }
}
