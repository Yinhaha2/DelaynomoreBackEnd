package com.agentcrawler.crawler.fallback;

import com.agentcrawler.crawler.model.CrawlResourceResult;

/**
 * Hardcoded site adapter used only after Kazumi plugins fail to produce videos.
 */
public interface SiteFallback {

    String name();

    boolean enabled();

    boolean matches(String site);

    CrawlResourceResult crawl(String keyword, int maxSearchResults, int maxEpisodesPerRoad);
}
