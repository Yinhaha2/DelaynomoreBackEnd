package com.agentcrawler.cache;

import com.agentcrawler.crawler.model.CrawlResourceResult;

/**
 * 命中的检索结果。dueForCheck 为真时仍可先返回旧结果，由调用方在后台异步校验。
 */
public record CacheLookup(CrawlResourceResult result, boolean dueForCheck) {
}
