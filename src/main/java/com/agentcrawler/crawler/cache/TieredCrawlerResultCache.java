package com.agentcrawler.crawler.cache;

import com.agentcrawler.config.CrawlerCacheProperties;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.DisposableBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Caffeine L1 + optional Redis L2. Redis failures never fail the crawl.
 */
public class TieredCrawlerResultCache implements CrawlerResultCache, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(TieredCrawlerResultCache.class);

    private final CrawlerResultCache local;
    private final RemoteKvStore remote;
    private final CrawlerCacheProperties properties;
    private final ObjectMapper mapper;

    public TieredCrawlerResultCache(
            CrawlerResultCache local,
            RemoteKvStore remote,
            CrawlerCacheProperties properties,
            ObjectMapper mapper
    ) {
        this.local = local;
        this.remote = remote;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public CrawlResourceResult getPlay(String lookupKey) {
        CrawlResourceResult localHit = local.getPlay(lookupKey);
        if (localHit != null) {
            return localHit;
        }
        CrawlResourceResult remoteHit = readRemote(playKey(lookupKey), CrawlResourceResult.class);
        if (remoteHit != null) {
            local.putPlay(lookupKey, remoteHit);
        }
        return remoteHit;
    }

    @Override
    public void putPlay(String lookupKey, CrawlResourceResult result) {
        local.putPlay(lookupKey, result);
        writeRemote(playKey(lookupKey), result, Duration.ofSeconds(properties.playTtlSeconds()));
    }

    @Override
    public CatalogSnapshot getCatalog(String lookupKey) {
        CatalogSnapshot localHit = local.getCatalog(lookupKey);
        if (localHit != null) {
            return localHit;
        }
        CatalogSnapshot remoteHit = readRemote(catalogKey(lookupKey), CatalogSnapshot.class);
        if (remoteHit != null) {
            local.putCatalog(lookupKey, remoteHit);
        }
        return remoteHit;
    }

    @Override
    public void putCatalog(String lookupKey, CatalogSnapshot snapshot) {
        local.putCatalog(lookupKey, snapshot);
        writeRemote(catalogKey(lookupKey), snapshot, Duration.ofSeconds(properties.catalogTtlSeconds()));
    }

    @Override
    public void destroy() {
        if (remote != null) {
            remote.close();
        }
    }

    private String playKey(String lookupKey) {
        return CrawlCacheKeys.playRedisKey(properties.keyPrefix(), lookupKey);
    }

    private String catalogKey(String lookupKey) {
        return CrawlCacheKeys.catalogRedisKey(properties.keyPrefix(), lookupKey);
    }

    private <T> T readRemote(String redisKey, Class<T> type) {
        if (remote == null) {
            return null;
        }
        try {
            String json = remote.get(redisKey);
            if (json == null || json.isBlank()) {
                return null;
            }
            return mapper.readValue(json, type);
        } catch (Exception ex) {
            log.warn("读取远程缓存失败 {}: {}", redisKey, ex.toString());
            return null;
        }
    }

    private void writeRemote(String redisKey, Object value, Duration ttl) {
        if (remote == null || value == null) {
            return;
        }
        try {
            remote.set(redisKey, mapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            log.warn("序列化缓存失败 {}: {}", redisKey, ex.toString());
        } catch (RuntimeException ex) {
            log.warn("写入远程缓存失败 {}: {}", redisKey, ex.toString());
        }
    }
}
