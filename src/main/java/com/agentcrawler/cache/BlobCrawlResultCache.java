package com.agentcrawler.cache;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BlobCrawlResultCache implements CrawlResultCache {
    static final String INDEX_KEY = "cache/v1/index.json";

    private static final Logger log = LoggerFactory.getLogger(BlobCrawlResultCache.class);

    private final ObjectBlobStore store;
    private final ObjectMapper objectMapper;
    private final long maxBytes;
    private final Clock clock;
    private final RevalidateSchedule schedule;
    private final CrawlHotIndex hotIndex;
    private CrawlCacheIndex index = new CrawlCacheIndex();
    private boolean indexLoaded;
    private boolean backupDirty;
    private long lastBackupEpochMs;

    public BlobCrawlResultCache(ObjectBlobStore store, ObjectMapper objectMapper, long maxBytes, Clock clock) {
        this(store, objectMapper, maxBytes, clock, RevalidateSchedule.disabled());
    }

    public BlobCrawlResultCache(
            ObjectBlobStore store,
            ObjectMapper objectMapper,
            long maxBytes,
            Clock clock,
            RevalidateSchedule schedule
    ) {
        this(store, objectMapper, maxBytes, clock, schedule, null);
    }

    public BlobCrawlResultCache(
            ObjectBlobStore store,
            ObjectMapper objectMapper,
            long maxBytes,
            Clock clock,
            RevalidateSchedule schedule,
            CrawlHotIndex hotIndex
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.maxBytes = maxBytes;
        this.clock = clock;
        this.schedule = schedule == null ? RevalidateSchedule.disabled() : schedule;
        this.hotIndex = hotIndex;
    }

    @Override
    public synchronized Optional<CrawlResourceResult> find(String keyword, String site) {
        return lookup(keyword, site).map(CacheLookup::result);
    }

    @Override
    public synchronized Optional<CacheLookup> lookup(String keyword, String site) {
        if (CacheObjectIds.blankKeyword(keyword)) {
            return Optional.empty();
        }
        String id = CacheObjectIds.of(keyword, site);
        String objectKey = objectKey(id);
        try {
            ensureRebuilt();
            Optional<byte[]> raw = store.get(objectKey);
            if (raw.isEmpty()) {
                if (index().remove(id)) {
                    if (usingHot()) {
                        hotIndex.remove(id);
                    }
                    persist();
                }
                return Optional.empty();
            }
            CrawlResourceResult result = objectMapper.readValue(raw.get(), CrawlResourceResult.class);
            CrawlCacheIndex current = index();
            long now = clock.millis();
            if (!current.touch(id, now)) {
                current.upsert(id, raw.get().length, now);
            }
            long revalidateBefore = current.revalidateAt(id);
            boolean due = markSchedule(current, id);
            long revalidateAfter = current.revalidateAt(id);
            if (!usingHot()) {
                persist();
                return Optional.of(new CacheLookup(result, due));
            }
            CrawlHotIndex.Touch touch = hotIndex.touch(id, now);
            if (touch.status() == CrawlHotIndex.Touch.Status.UNAVAILABLE) {
                persist();
                return Optional.of(new CacheLookup(result, due));
            }
            if (touch.status() == CrawlHotIndex.Touch.Status.MISSING) {
                hotIndex.upsert(id, raw.get().length, now, revalidateAfter);
                backupDirty = true;
            } else if (revalidateAfter != revalidateBefore) {
                hotIndex.updateRevalidateAt(id, revalidateAfter);
                backupDirty = true;
            } else if (touch.status() == CrawlHotIndex.Touch.Status.UPDATED) {
                backupDirty = true;
            }
            return Optional.of(new CacheLookup(result, due));
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
            ensureRebuilt();
            byte[] body = objectMapper.writeValueAsBytes(result);
            String id = CacheObjectIds.of(keyword, site);
            store.put(objectKey(id), body);
            CrawlCacheIndex current = index();
            long now = clock.millis();
            current.upsert(id, body.length, now);
            if (schedule.enabled()) {
                current.schedule(id, schedule.next(now));
            }
            boolean tracked = usingHot() && hotIndex.upsert(id, body.length, now, current.revalidateAt(id));
            if (!tracked) {
                List<String> removed = current.evictDownTo(maxBytes, id);
                for (String oldId : removed) {
                    try {
                        store.delete(objectKey(oldId));
                    } catch (RuntimeException ex) {
                        log.warn("删除旧检索缓存失败: {}", ex.toString());
                    }
                }
            }
            persist();
            backupDirty = false;
            lastBackupEpochMs = now;
        } catch (Exception ex) {
            log.warn("写入检索缓存失败: {}", ex.toString());
        }
    }

    @Override
    public synchronized void invalidate(String keyword, String site) {
        if (CacheObjectIds.blankKeyword(keyword)) {
            return;
        }
        String id = CacheObjectIds.of(keyword, site);
        ensureRebuilt();
        try {
            store.delete(objectKey(id));
        } catch (RuntimeException ex) {
            log.warn("删除失效检索缓存失败: {}", ex.toString());
        }
        index().remove(id);
        if (usingHot()) {
            hotIndex.remove(id);
        }
        persist();
        backupDirty = false;
        lastBackupEpochMs = clock.millis();
    }

    @Override
    public synchronized void postpone(String keyword, String site, boolean soon) {
        if (!schedule.enabled() || CacheObjectIds.blankKeyword(keyword)) {
            return;
        }
        String id = CacheObjectIds.of(keyword, site);
        long at = soon ? schedule.soon(clock.millis()) : schedule.next(clock.millis());
        if (index().schedule(id, at)) {
            if (usingHot()) {
                hotIndex.updateRevalidateAt(id, at);
            }
            persist();
            backupDirty = false;
            lastBackupEpochMs = clock.millis();
        }
    }

    @Override
    public synchronized void evictOverflow() {
        if (!usingHot() || hotIndex.usedBytes() <= maxBytes) {
            return;
        }
        if (!hotIndex.tryEvictLock()) {
            return;
        }
        try {
            int removed = 0;
            int rounds = 0;
            while (hotIndex.usedBytes() > maxBytes && rounds++ < 64) {
                List<CrawlHotIndex.HotEntry> oldest = hotIndex.oldest(32);
                if (oldest.isEmpty()) {
                    break;
                }
                long live = hotIndex.count();
                boolean progressed = false;
                for (CrawlHotIndex.HotEntry victim : oldest) {
                    if (hotIndex.usedBytes() <= maxBytes || live <= 1) {
                        break;
                    }
                    try {
                        store.delete(objectKey(victim.id()));
                    } catch (RuntimeException ex) {
                        log.warn("删除旧检索缓存失败: {}", ex.toString());
                    }
                    hotIndex.remove(victim.id());
                    index().remove(victim.id());
                    live--;
                    removed++;
                    progressed = true;
                }
                if (!progressed) {
                    break;
                }
            }
            if (removed > 0) {
                log.info("对象缓存超过上限，后台已删除 {} 条最久未使用的结果", removed);
                persist();
                backupDirty = false;
                lastBackupEpochMs = clock.millis();
            }
        } finally {
            hotIndex.unlockEvict();
        }
    }

    @Override
    public synchronized void flushBackupIfStale(long minIntervalMillis) {
        if (!backupDirty || !usingHot()) {
            return;
        }
        long now = clock.millis();
        if (now - lastBackupEpochMs < minIntervalMillis) {
            return;
        }
        persist();
        backupDirty = false;
        lastBackupEpochMs = now;
    }

    public void closeHotIndex() {
        if (hotIndex != null) {
            hotIndex.close();
        }
    }

    private boolean usingHot() {
        return hotIndex != null && hotIndex.available();
    }

    private void ensureRebuilt() {
        if (!usingHot() || !hotIndex.isEmpty()) {
            return;
        }
        CrawlCacheIndex current = index();
        if (current.getEntries().isEmpty()) {
            return;
        }
        if (!hotIndex.tryEvictLock()) {
            return;
        }
        try {
            if (!hotIndex.isEmpty()) {
                return;
            }
            List<CrawlHotIndex.HotEntry> entries = new ArrayList<>();
            for (CrawlCacheIndex.Entry entry : current.getEntries()) {
                entries.add(new CrawlHotIndex.HotEntry(
                        entry.getKey(),
                        entry.getBytes(),
                        entry.getLastAccessEpochMs(),
                        entry.getRevalidateAtEpochMs()
                ));
            }
            hotIndex.rebuild(entries);
            log.info("Redis 热索引为空，已从对象存储索引重建 {} 条", entries.size());
        } finally {
            hotIndex.unlockEvict();
        }
    }

    private boolean markSchedule(CrawlCacheIndex current, String id) {
        if (!schedule.enabled()) {
            return false;
        }
        long at = current.revalidateAt(id);
        long now = clock.millis();
        if (at <= 0) {
            current.schedule(id, schedule.next(now));
            return false;
        }
        return now >= at;
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
            if (usingHot()) {
                List<CrawlHotIndex.HotEntry> snap = hotIndex.snapshot();
                if (snap != null) {
                    CrawlCacheIndex exported = new CrawlCacheIndex();
                    for (CrawlHotIndex.HotEntry entry : snap) {
                        exported.upsert(entry.id(), entry.bytes(), entry.lastAccessEpochMs());
                        if (entry.revalidateAtEpochMs() > 0) {
                            exported.schedule(entry.id(), entry.revalidateAtEpochMs());
                        }
                    }
                    index = exported;
                    indexLoaded = true;
                    store.put(INDEX_KEY, objectMapper.writeValueAsBytes(exported));
                    return;
                }
            }
            store.put(INDEX_KEY, objectMapper.writeValueAsBytes(index));
        } catch (Exception ex) {
            log.warn("写入检索缓存索引失败: {}", ex.toString());
        }
    }

    private static String objectKey(String id) {
        return "cache/v1/objects/" + id + ".json";
    }
}
