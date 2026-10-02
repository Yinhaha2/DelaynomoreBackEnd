package com.agentcrawler.cache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlCacheIndexTest {

    @Test
    void evictsLeastRecentlyUsedAndKeepsTheEntryJustWritten() {
        CrawlCacheIndex index = new CrawlCacheIndex();
        index.upsert("old", 80, 1);
        index.upsert("mid", 80, 2);
        index.upsert("new", 80, 3);

        List<String> removed = index.evictDownTo(100, "new");

        assertEquals(List.of("old", "mid"), removed);
        assertEquals(80, index.totalBytes());
        assertEquals("new", index.getEntries().get(0).getKey());
    }

    @Test
    void keepsASingleOversizedEntry() {
        CrawlCacheIndex index = new CrawlCacheIndex();
        index.upsert("only", 500, 1);

        List<String> removed = index.evictDownTo(100, "only");

        assertTrue(removed.isEmpty());
        assertEquals(500, index.totalBytes());
    }

    @Test
    void touchMovesAnEntryOutOfTheEvictionOrder() {
        CrawlCacheIndex index = new CrawlCacheIndex();
        index.upsert("a", 60, 1);
        index.upsert("b", 60, 2);
        index.touch("a", 10);

        List<String> removed = index.evictDownTo(60, "a");

        assertEquals(List.of("b"), removed);
        assertEquals("a", index.getEntries().get(0).getKey());
    }
}
