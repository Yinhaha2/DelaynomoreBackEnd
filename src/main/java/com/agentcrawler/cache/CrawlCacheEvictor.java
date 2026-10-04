package com.agentcrawler.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 容量清理不走用户请求。热门 key 的访问时间也在这里隔几分钟写回对象存储备份。
 */
@Component
@ConditionalOnProperty(prefix = "agent.crawler.cache.redis", name = "enabled", havingValue = "true")
public class CrawlCacheEvictor {
    static final long BACKUP_INTERVAL_MS = 300_000L;

    private final org.springframework.beans.factory.ObjectProvider<CrawlResultCache> caches;

    public CrawlCacheEvictor(org.springframework.beans.factory.ObjectProvider<CrawlResultCache> caches) {
        this.caches = caches;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void maintain() {
        CrawlResultCache cache = caches.getIfAvailable();
        if (cache != null) {
            cache.maintain(BACKUP_INTERVAL_MS);
        }
    }
}
