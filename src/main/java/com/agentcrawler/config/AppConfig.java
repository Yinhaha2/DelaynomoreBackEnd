package com.agentcrawler.config;

import com.agentcrawler.crawler.cache.CaffeineCrawlerResultCache;
import com.agentcrawler.crawler.cache.CrawlerResultCache;
import com.agentcrawler.crawler.cache.LettuceRemoteKvStore;
import com.agentcrawler.crawler.cache.TieredCrawlerResultCache;
import com.agentcrawler.crawler.reliability.CrawlSingleflight;
import com.agentcrawler.crawler.reliability.SiteCircuitBoard;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, CrawlerReliabilityProperties.class, CrawlerCacheProperties.class})
public class AppConfig {
    @Bean
    SiteCircuitBoard siteCircuitBoard(CrawlerReliabilityProperties reliability) {
        return new SiteCircuitBoard(reliability);
    }

    @Bean
    CrawlSingleflight crawlSingleflight() {
        return new CrawlSingleflight();
    }

    @Bean
    CrawlerResultCache crawlerResultCache(CrawlerCacheProperties cacheProperties) {
        if (!cacheProperties.enabled()) {
            return CrawlerResultCache.noop();
        }
        CaffeineCrawlerResultCache local = new CaffeineCrawlerResultCache(cacheProperties);
        if (!cacheProperties.redis().enabled()) {
            return local;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new TieredCrawlerResultCache(
                local,
                new LettuceRemoteKvStore(cacheProperties.redis()),
                cacheProperties,
                mapper
        );
    }
}
