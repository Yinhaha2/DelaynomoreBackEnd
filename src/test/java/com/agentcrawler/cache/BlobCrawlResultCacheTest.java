package com.agentcrawler.cache;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlobCrawlResultCacheTest {

    @Test
    void roundTripsAndDropsTheOlderObjectWhenTheCapIsReached() {
        MapStore store = new MapStore();
        MutableClock clock = new MutableClock();
        BlobCrawlResultCache cache = new BlobCrawlResultCache(store, new ObjectMapper(), 1, clock);

        cache.save("葬送的芙莉莲", "DM84", sample("葬送的芙莉莲", "https://cdn.example/a.m3u8"));
        clock.plusSeconds(5);
        cache.save("鬼灭之刃", "DM84", sample("鬼灭之刃", "https://cdn.example/b.m3u8"));

        assertFalse(cache.find("葬送的芙莉莲", "DM84").isPresent());
        CrawlResourceResult hit = cache.find("鬼灭之刃", "DM84").orElseThrow();
        assertEquals("https://cdn.example/b.m3u8", hit.videos().get(0).url());
        assertTrue(store.data.keySet().stream().noneMatch(key -> key.contains(CacheObjectIds.of("葬送的芙莉莲", "DM84"))));
    }

    @Test
    void sameKeywordWithDifferentSpacingSharesOneObject() {
        MapStore store = new MapStore();
        BlobCrawlResultCache cache = new BlobCrawlResultCache(
                store,
                new ObjectMapper(),
                10_000,
                Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC)
        );
        cache.save("葬送的芙莉莲", "DM84", sample("葬送的芙莉莲", "https://cdn.example/a.m3u8"));

        CrawlResourceResult hit = cache.find("  葬送的芙莉莲  ", "DM84").orElseThrow();
        assertEquals("葬送的芙莉莲", hit.keyword());
    }

    @Test
    void softExpiryStaysReadableUntilTheCheckTimeThenInvalidateDropsIndexAndObject() {
        MapStore store = new MapStore();
        MutableClock clock = new MutableClock();
        BlobCrawlResultCache cache = new BlobCrawlResultCache(
                store,
                new ObjectMapper(),
                10_000,
                clock,
                RevalidateSchedule.of(1_000, 0)
        );
        cache.save("葬送的芙莉莲", "DM84", sample("葬送的芙莉莲", "https://cdn.example/a.m3u8"));

        CacheLookup fresh = cache.lookup("葬送的芙莉莲", "DM84").orElseThrow();
        assertFalse(fresh.dueForCheck());

        clock.plusSeconds(2);
        CacheLookup due = cache.lookup("葬送的芙莉莲", "DM84").orElseThrow();
        assertTrue(due.dueForCheck());
        assertEquals("https://cdn.example/a.m3u8", due.result().videos().get(0).url());

        cache.invalidate("葬送的芙莉莲", "DM84");
        assertFalse(cache.find("葬送的芙莉莲", "DM84").isPresent());
        String id = CacheObjectIds.of("葬送的芙莉莲", "DM84");
        assertTrue(store.data.keySet().stream().noneMatch(key -> key.contains(id)));
        assertFalse(new String(store.data.get(BlobCrawlResultCache.INDEX_KEY)).contains(id));
    }

    @Test
    void legacyEntryWithoutACheckTimeIsScheduledInsteadOfExpiringImmediately() {
        MapStore store = new MapStore();
        MutableClock clock = new MutableClock();
        ObjectMapper mapper = new ObjectMapper();
        new BlobCrawlResultCache(store, mapper, 10_000, clock)
                .save("葬送的芙莉莲", "DM84", sample("葬送的芙莉莲", "https://cdn.example/a.m3u8"));

        BlobCrawlResultCache scheduled = new BlobCrawlResultCache(
                store,
                mapper,
                10_000,
                clock,
                RevalidateSchedule.of(60_000, 0)
        );
        assertFalse(scheduled.lookup("葬送的芙莉莲", "DM84").orElseThrow().dueForCheck());
    }

    @Test
    void hotIndexHitDoesNotRewriteTheBackupUntilTheAccessTimeActuallyMoves() {
        MapStore store = new MapStore();
        MutableClock clock = new MutableClock();
        MemoryCrawlHotIndex hot = new MemoryCrawlHotIndex();
        BlobCrawlResultCache cache = new BlobCrawlResultCache(
                store,
                new ObjectMapper(),
                10_000,
                clock,
                RevalidateSchedule.disabled(),
                hot
        );
        cache.save("葬送的芙莉莲", "DM84", sample("葬送的芙莉莲", "https://cdn.example/a.m3u8"));
        int writesAfterSave = store.puts;

        cache.find("葬送的芙莉莲", "DM84");
        cache.find("葬送的芙莉莲", "DM84");
        assertEquals(writesAfterSave, store.puts);

        clock.plusSeconds(61);
        cache.find("葬送的芙莉莲", "DM84");
        assertEquals(writesAfterSave, store.puts);
        cache.flushBackupIfStale(0);
        assertTrue(store.puts > writesAfterSave);
        assertEquals(1, hot.count());
    }

    @Test
    void overflowIsRemovedByTheBackgroundPassAndKeepsASingleOversizedObject() {
        MapStore store = new MapStore();
        MutableClock clock = new MutableClock();
        MemoryCrawlHotIndex hot = new MemoryCrawlHotIndex();
        BlobCrawlResultCache cache = new BlobCrawlResultCache(
                store,
                new ObjectMapper(),
                1,
                clock,
                RevalidateSchedule.disabled(),
                hot
        );
        cache.save("葬送的芙莉莲", "DM84", sample("葬送的芙莉莲", "https://cdn.example/a.m3u8"));
        clock.plusSeconds(5);
        cache.save("鬼灭之刃", "DM84", sample("鬼灭之刃", "https://cdn.example/b.m3u8"));
        assertTrue(cache.find("葬送的芙莉莲", "DM84").isPresent());
        assertTrue(cache.find("鬼灭之刃", "DM84").isPresent());

        cache.evictOverflow();

        assertFalse(cache.find("葬送的芙莉莲", "DM84").isPresent());
        assertTrue(cache.find("鬼灭之刃", "DM84").isPresent());
        assertEquals(1, hot.count());
    }

    @Test
    void emptyHotIndexIsRebuiltFromTheObjectBackup() {
        MapStore store = new MapStore();
        MutableClock clock = new MutableClock();
        ObjectMapper mapper = new ObjectMapper();
        new BlobCrawlResultCache(store, mapper, 10_000, clock)
                .save("葬送的芙莉莲", "DM84", sample("葬送的芙莉莲", "https://cdn.example/a.m3u8"));

        MemoryCrawlHotIndex hot = new MemoryCrawlHotIndex();
        BlobCrawlResultCache warm = new BlobCrawlResultCache(
                store,
                mapper,
                10_000,
                clock,
                RevalidateSchedule.disabled(),
                hot
        );
        assertEquals("https://cdn.example/a.m3u8", warm.find("葬送的芙莉莲", "DM84").orElseThrow().videos().get(0).url());
        assertEquals(1, hot.count());
        assertTrue(hot.usedBytes() > 0);
    }

    private static CrawlResourceResult sample(String title, String url) {
        return new CrawlResourceResult(
                title,
                "DM84",
                "DM84",
                java.util.List.of(new CrawlResourceResult.VideoResource("第1集", url, "http://page", "线路1")),
                java.util.List.of(),
                java.util.List.of()
        );
    }

    private static final class MapStore implements ObjectBlobStore {
        private final Map<String, byte[]> data = new HashMap<>();
        private int puts;

        @Override
        public Optional<byte[]> get(String key) {
            byte[] body = data.get(key);
            return body == null ? Optional.empty() : Optional.of(body);
        }

        @Override
        public void put(String key, byte[] body) {
            puts++;
            data.put(key, body);
        }

        @Override
        public void delete(String key) {
            data.remove(key);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-10-02T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void plusSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
