package com.agentcrawler.crawler.cache;

import com.agentcrawler.config.CrawlerCacheProperties;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

public class CaffeineCrawlerResultCache implements CrawlerResultCache {
    private final Cache<String, CrawlResourceResult> play;
    private final Cache<String, CatalogSnapshot> catalog;

    public CaffeineCrawlerResultCache(CrawlerCacheProperties properties) {
        this.play = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.playTtlSeconds()))
                .maximumSize(properties.playMaxEntries())
                .build();
        this.catalog = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.catalogTtlSeconds()))
                .maximumSize(properties.catalogMaxEntries())
                .build();
    }

    @Override
    public CrawlResourceResult getPlay(String lookupKey) {
        if (lookupKey == null || lookupKey.isBlank()) {
            return null;
        }
        return play.getIfPresent(lookupKey);
    }

    @Override
    public void putPlay(String lookupKey, CrawlResourceResult result) {
        if (lookupKey == null || lookupKey.isBlank() || result == null) {
            return;
        }
        if (result.videos() == null || result.videos().isEmpty()) {
            return;
        }
        play.put(lookupKey, result);
    }

    @Override
    public CatalogSnapshot getCatalog(String lookupKey) {
        if (lookupKey == null || lookupKey.isBlank()) {
            return null;
        }
        return catalog.getIfPresent(lookupKey);
    }

    @Override
    public void putCatalog(String lookupKey, CatalogSnapshot snapshot) {
        if (lookupKey == null || lookupKey.isBlank() || snapshot == null) {
            return;
        }
        catalog.put(lookupKey, snapshot);
    }
}
