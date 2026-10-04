package com.agentcrawler.cache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlHotIndexTest {

    @Test
    void touchIsThrottledInsideOneMinuteAndUsedBytesStayAtomic() {
        MemoryCrawlHotIndex hot = new MemoryCrawlHotIndex();
        assertTrue(hot.upsert("a", 10, 1_000, 0));
        assertTrue(hot.upsert("a", 40, 1_000, 5_000));
        assertEquals(40, hot.usedBytes());

        CrawlHotIndex.Touch throttled = hot.touch("a", 1_000 + 30_000);
        assertEquals(CrawlHotIndex.Touch.Status.THROTTLED, throttled.status());
        assertEquals(1_000, hot.oldest(1).get(0).lastAccessEpochMs());

        CrawlHotIndex.Touch updated = hot.touch("a", 1_000 + CrawlHotIndex.TOUCH_WINDOW_MS);
        assertEquals(CrawlHotIndex.Touch.Status.UPDATED, updated.status());
        assertEquals(1_000 + CrawlHotIndex.TOUCH_WINDOW_MS, hot.oldest(1).get(0).lastAccessEpochMs());

        assertTrue(hot.remove("a"));
        assertEquals(0, hot.usedBytes());
        assertEquals(CrawlHotIndex.Touch.Status.MISSING, hot.touch("a", 9_000).status());
    }

    @Test
    void oldestComesFirstAndASecondEvictLockIsRejected() {
        MemoryCrawlHotIndex hot = new MemoryCrawlHotIndex();
        hot.upsert("new", 10, 50, 0);
        hot.upsert("old", 10, 10, 0);
        assertEquals(List.of("old", "new"), hot.oldest(5).stream().map(CrawlHotIndex.HotEntry::id).toList());

        assertTrue(hot.tryEvictLock());
        assertFalse(hot.tryEvictLock());
        hot.unlockEvict();
        assertTrue(hot.tryEvictLock());
    }

    @Test
    void rebuildReplacesTheDirectoryFromTheBackup() {
        MemoryCrawlHotIndex hot = new MemoryCrawlHotIndex();
        hot.upsert("gone", 5, 1, 0);
        hot.rebuild(List.of(new CrawlHotIndex.HotEntry("kept", 8, 20, 90)));

        assertEquals(1, hot.count());
        assertEquals(8, hot.usedBytes());
        assertEquals(90, hot.touch("kept", 20).revalidateAtEpochMs());
        assertEquals(CrawlHotIndex.Touch.Status.THROTTLED, hot.touch("kept", 20).status());
    }
}
