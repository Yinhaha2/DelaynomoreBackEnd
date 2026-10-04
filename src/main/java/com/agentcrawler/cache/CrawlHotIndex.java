package com.agentcrawler.cache;

import java.util.List;

/**
 * 对象缓存的热索引。Hash 记目录和大小，ZSet 用最近访问时间做 LRU。
 * 对象存储里的 index.json 只是备份。
 */
public interface CrawlHotIndex {
    /** 同一个 key 在这个窗口内只更新一次访问时间。 */
    long TOUCH_WINDOW_MS = 60_000L;

    boolean available();

    boolean isEmpty();

    Touch touch(String id, long nowMillis);

    boolean upsert(String id, long bytes, long lastAccessEpochMs, long revalidateAtEpochMs);

    void updateRevalidateAt(String id, long revalidateAtEpochMs);

    boolean remove(String id);

    long usedBytes();

    long count();

    List<HotEntry> oldest(int limit);

    void rebuild(List<HotEntry> entries);

    /** Redis 不可用时返回 null，调用方改写内存里的索引。 */
    List<HotEntry> snapshot();

    boolean tryEvictLock();

    void unlockEvict();

    void close();

    record HotEntry(String id, long bytes, long lastAccessEpochMs, long revalidateAtEpochMs) {
    }

    record Touch(Status status, long revalidateAtEpochMs) {
        public enum Status {
            MISSING,
            UPDATED,
            THROTTLED,
            UNAVAILABLE
        }

        static Touch missing() {
            return new Touch(Status.MISSING, 0);
        }

        static Touch unavailable() {
            return new Touch(Status.UNAVAILABLE, 0);
        }
    }
}
