package com.agentcrawler.cache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进程内热索引，语义和 Redis 实现一致，测试和未连上 Redis 时不使用它。
 */
public final class MemoryCrawlHotIndex implements CrawlHotIndex {
    private final Map<String, HotEntry> entries = new LinkedHashMap<>();
    private long used;
    private String lockToken;

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public synchronized Touch touch(String id, long nowMillis) {
        HotEntry existing = entries.get(id);
        if (existing == null) {
            return Touch.missing();
        }
        if (nowMillis - existing.lastAccessEpochMs() < TOUCH_WINDOW_MS) {
            return new Touch(Touch.Status.THROTTLED, existing.revalidateAtEpochMs());
        }
        HotEntry updated = new HotEntry(id, existing.bytes(), nowMillis, existing.revalidateAtEpochMs());
        entries.put(id, updated);
        return new Touch(Touch.Status.UPDATED, updated.revalidateAtEpochMs());
    }

    @Override
    public synchronized boolean upsert(String id, long bytes, long lastAccessEpochMs, long revalidateAtEpochMs) {
        HotEntry previous = entries.get(id);
        if (previous != null) {
            used -= Math.max(0, previous.bytes());
        }
        used += Math.max(0, bytes);
        if (used < 0) {
            used = 0;
        }
        entries.put(id, new HotEntry(id, bytes, lastAccessEpochMs, revalidateAtEpochMs));
        return true;
    }

    @Override
    public synchronized void updateRevalidateAt(String id, long revalidateAtEpochMs) {
        HotEntry existing = entries.get(id);
        if (existing == null) {
            return;
        }
        entries.put(id, new HotEntry(id, existing.bytes(), existing.lastAccessEpochMs(), revalidateAtEpochMs));
    }

    @Override
    public synchronized boolean remove(String id) {
        HotEntry removed = entries.remove(id);
        if (removed == null) {
            return false;
        }
        used -= Math.max(0, removed.bytes());
        if (used < 0) {
            used = 0;
        }
        return true;
    }

    @Override
    public synchronized long usedBytes() {
        return used;
    }

    @Override
    public synchronized long count() {
        return entries.size();
    }

    @Override
    public synchronized List<HotEntry> oldest(int limit) {
        if (limit <= 0 || entries.isEmpty()) {
            return List.of();
        }
        return entries.values().stream()
                .sorted(Comparator.comparingLong(HotEntry::lastAccessEpochMs))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized void rebuild(List<HotEntry> incoming) {
        entries.clear();
        used = 0;
        if (incoming == null) {
            return;
        }
        for (HotEntry entry : incoming) {
            if (entry == null || entry.id() == null || entry.id().isBlank()) {
                continue;
            }
            upsert(entry.id(), entry.bytes(), entry.lastAccessEpochMs(), entry.revalidateAtEpochMs());
        }
    }

    @Override
    public synchronized List<HotEntry> snapshot() {
        return new ArrayList<>(entries.values());
    }

    @Override
    public synchronized boolean tryEvictLock() {
        if (lockToken != null) {
            return false;
        }
        lockToken = "held";
        return true;
    }

    @Override
    public synchronized void unlockEvict() {
        lockToken = null;
    }

    @Override
    public void close() {
    }
}
