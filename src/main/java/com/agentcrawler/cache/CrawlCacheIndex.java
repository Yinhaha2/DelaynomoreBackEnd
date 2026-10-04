package com.agentcrawler.cache;

import java.util.ArrayList;
import java.util.List;

/**
 * 缓存索引。占用达到上限时，按最久没被用到的顺序删，刚写入的那条留到最后。
 */
final class CrawlCacheIndex {
    private List<Entry> entries = new ArrayList<>();

    public CrawlCacheIndex() {}

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    long totalBytes() {
        long total = 0;
        for (Entry entry : entries) {
            total += Math.max(0, entry.bytes);
        }
        return total;
    }

    void upsert(String key, long bytes, long now) {
        Entry existing = find(key);
        if (existing == null) {
            entries.add(new Entry(key, bytes, now));
            return;
        }
        existing.bytes = bytes;
        existing.lastAccessEpochMs = now;
    }

    boolean touch(String key, long now) {
        Entry existing = find(key);
        if (existing == null) {
            return false;
        }
        existing.lastAccessEpochMs = now;
        return true;
    }

    boolean remove(String key) {
        return entries.removeIf(entry -> key.equals(entry.key));
    }

    boolean schedule(String key, long revalidateAtEpochMs) {
        Entry existing = find(key);
        if (existing == null) {
            return false;
        }
        existing.revalidateAtEpochMs = revalidateAtEpochMs;
        return true;
    }

    long revalidateAt(String key) {
        Entry existing = find(key);
        return existing == null ? 0 : existing.revalidateAtEpochMs;
    }

    List<String> evictDownTo(long maxBytes, String protectKey) {
        List<String> removed = new ArrayList<>();
        while (totalBytes() > maxBytes) {
            Entry victim = oldestExcept(protectKey);
            if (victim == null) {
                break;
            }
            entries.remove(victim);
            removed.add(victim.key);
        }
        return removed;
    }

    private Entry oldestExcept(String protectKey) {
        Entry oldest = null;
        for (Entry entry : entries) {
            if (protectKey != null && protectKey.equals(entry.key) && entries.size() > 1) {
                continue;
            }
            if (oldest == null || entry.lastAccessEpochMs < oldest.lastAccessEpochMs) {
                oldest = entry;
            }
        }
        if (oldest != null && protectKey != null && protectKey.equals(oldest.key)) {
            return null;
        }
        return oldest;
    }

    private Entry find(String key) {
        for (Entry entry : entries) {
            if (key.equals(entry.key)) {
                return entry;
            }
        }
        return null;
    }

    static final class Entry {
        private String key;
        private long bytes;
        private long lastAccessEpochMs;
        /** 软过期时间。0 表示还没排过校验，读到时再补上，避免旧数据同时过期。 */
        private long revalidateAtEpochMs;

        public Entry() {}

        Entry(String key, long bytes, long lastAccessEpochMs) {
            this.key = key;
            this.bytes = bytes;
            this.lastAccessEpochMs = lastAccessEpochMs;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public long getBytes() {
            return bytes;
        }

        public void setBytes(long bytes) {
            this.bytes = bytes;
        }

        public long getLastAccessEpochMs() {
            return lastAccessEpochMs;
        }

        public void setLastAccessEpochMs(long lastAccessEpochMs) {
            this.lastAccessEpochMs = lastAccessEpochMs;
        }

        public long getRevalidateAtEpochMs() {
            return revalidateAtEpochMs;
        }

        public void setRevalidateAtEpochMs(long revalidateAtEpochMs) {
            this.revalidateAtEpochMs = revalidateAtEpochMs;
        }
    }
}
