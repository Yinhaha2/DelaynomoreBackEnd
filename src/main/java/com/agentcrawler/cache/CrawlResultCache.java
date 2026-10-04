package com.agentcrawler.cache;

import com.agentcrawler.crawler.model.CrawlResourceResult;

import java.util.Optional;

/**
 * 按关键词和站点保存检索结果。对象本身不按硬 TTL 删除，
 * 软过期后仍可返回旧结果，空间上限由实现自己处理。
 */
public interface CrawlResultCache {
    Optional<CrawlResourceResult> find(String keyword, String site);

    void save(String keyword, String site, CrawlResourceResult result);

    default Optional<CacheLookup> lookup(String keyword, String site) {
        return find(keyword, site).map(result -> new CacheLookup(result, false));
    }

    /** 确认失效后删掉对象，并同步从索引里去掉，避免已用空间对不上。 */
    default void invalidate(String keyword, String site) {
    }

    /** soon 为真时把下次校验排得更近，用于探测没有明确结论的情况。 */
    default void postpone(String keyword, String site, boolean soon) {
    }
}
