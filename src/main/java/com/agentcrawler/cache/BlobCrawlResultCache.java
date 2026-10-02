package com.agentcrawler.cache;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public final class BlobCrawlResultCache implements CrawlResultCache {
    static final String INDEX_KEY = "cache/v1/index.json";

    private static final Logger log = LoggerFactory.getLogger(BlobCrawlResultCache.class);

    private final ObjectBlobStore store;
    private final ObjectMapper objectMapper;
    private final long maxBytes;
    private final Clock clock;
    private CrawlCacheIndex index = new CrawlCacheIndex();
    private boolean indexLoaded;

    public BlobCrawlResultCache(ObjectBlobStore store, ObjectMapper objectMapper, long maxBytes, Clock clock) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.maxBytes = maxBytes;
        this.clock = clock;
    }

    @Override
    public synchronized Optional<CrawlResourceResult> find(String keyword, String site) {
        if (CacheObjectIds.blankKeyword(keyword)) {
            return Optional.empty();
        }
        String id = CacheObjectIds.of(keyword, site);
        String objectKey = objectKey(id);
        try {
            Optional<byte[]> raw = store.get(objectKey);
            if (raw.isEmpty()) {
                if (index().remove(id)) {
                    persist();
                }
                return Optional.empty();
            }
            CrawlResourceResult result = objectMapper.readValue(raw.get(), CrawlResourceResult.class);
            CrawlCacheIndex current = index();
            if (!current.touch(id, clock.millis())) {
                current.upsert(id, raw.get().length, clock.millis());
            }
            persist();
            return Optional.of(result);
        } catch (Exception ex) {
            log.warn("读取检索缓存失败: {}", ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void save(String keyword, String site, CrawlResourceResult result) {
        if (CacheObjectIds.blankKeyword(keyword) || result == null) {
            return;
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(result);
            String id = CacheObjectIds.of(keyword, site);
            store.put(objectKey(id), body);
            CrawlCacheIndex current = index();
            current.upsert(id, body.length, clock.millis());
            List<String> removed = current.evictDownTo(maxBytes, id);
            for (String oldId : removed) {
                try {
                    store.delete(objectKey(oldId));
                } catch (RuntimeException ex) {
                    log.warn("删除旧检索缓存失败: {}", ex.toString());
                }
            }
            persist();
        } catch (Exception ex) {
            log.warn("写入检索缓存失败: {}", ex.toString());
        }
    }

    private CrawlCacheIndex index() {
        if (indexLoaded) {
            return index;
        }
        indexLoaded = true;
        try {
            Optional<byte[]> raw = store.get(INDEX_KEY);
            if (raw.isPresent() && raw.get().length > 0) {
                CrawlCacheIndex loaded = objectMapper.readValue(raw.get(), CrawlCacheIndex.class);
                if (loaded != null && loaded.getEntries() != null) {
                    index = loaded;
                }
            }
        } catch (Exception ex) {
            log.warn("读取检索缓存索引失败，按空索引继续: {}", ex.toString());
            index = new CrawlCacheIndex();
        }
        return index;
    }

    private void persist() {
        try {
            store.put(INDEX_KEY, objectMapper.writeValueAsBytes(index));
        } catch (Exception ex) {
            log.warn("写入检索缓存索引失败: {}", ex.toString());
        }
    }

    private static String objectKey(String id) {
        return "cache/v1/objects/" + id + ".json";
    }
}
