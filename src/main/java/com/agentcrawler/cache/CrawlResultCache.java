package com.agentcrawler.cache;

import com.agentcrawler.crawler.model.CrawlResourceResult;

import java.util.Optional;

/**
 * 按关键词和站点保存检索结果。没有时间过期，空间上限由实现自己处理。
 */
public interface CrawlResultCache {
    Optional<CrawlResourceResult> find(String keyword, String site);

    void save(String keyword, String site, CrawlResourceResult result);
}
