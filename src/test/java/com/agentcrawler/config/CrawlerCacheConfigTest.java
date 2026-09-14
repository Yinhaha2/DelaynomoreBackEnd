package com.agentcrawler.config;

import com.agentcrawler.crawler.cache.CaffeineCrawlerResultCache;
import com.agentcrawler.crawler.cache.CrawlerResultCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CrawlerCacheConfigTest {
    @Autowired
    private CrawlerResultCache crawlerResultCache;

    @Autowired
    private CrawlerCacheProperties cacheProperties;

    @Test
    void testProfileUsesCaffeineWithoutRedis() {
        assertThat(crawlerResultCache).isInstanceOf(CaffeineCrawlerResultCache.class);
        assertThat(cacheProperties.enabled()).isTrue();
        assertThat(cacheProperties.redis().enabled()).isFalse();
        assertThat(cacheProperties.playTtlSeconds()).isEqualTo(600);
        assertThat(cacheProperties.catalogTtlSeconds()).isEqualTo(28800);
        assertThat(cacheProperties.keyPrefix()).isEqualTo("acrawl");
    }
}
