package com.agentcrawler.crawler.cache;

import com.agentcrawler.config.CrawlerCacheProperties;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerResultCacheTest {

    @Test
    void catalogOmitsSignedPlayUrls() {
        CrawlResourceResult result = signedPlay();
        CatalogSnapshot snapshot = CatalogSnapshot.from(result);
        assertThat(snapshot.episodeCount()).isEqualTo(1);
        assertThat(snapshot.sources()).isNotEmpty();
        snapshot.sources().forEach(source -> source.episodes().forEach(episode -> {
            assertThat(episode.title()).isEqualTo("第01集");
            assertThat(episode.sourcePage()).contains("silisili.link");
        }));
        assertThat(snapshot.toString()).doesNotContain("X-Amz-Signature");
        assertThat(snapshot.toString()).doesNotContain("cloudflarestorage");
    }

    @Test
    void clampsPlayTtlSoSignedUrlsCannotLiveADay() {
        CrawlerCacheProperties properties = new CrawlerCacheProperties(
                true, 86400, 60, 10, 10, "acrawl", CrawlerCacheProperties.Redis.disabled()
        );
        assertThat(properties.playTtlSeconds()).isEqualTo(900);
        assertThat(properties.catalogTtlSeconds()).isEqualTo(3600);
    }

    @Test
    void caffeineStoresPlayAndCatalogSeparately() {
        CaffeineCrawlerResultCache cache = new CaffeineCrawlerResultCache(CrawlerCacheProperties.caffeineOnly());
        CrawlResourceResult result = sample();
        cache.putPlay("sili:鬼灭之刃", result);
        cache.putCatalog("sili:鬼灭之刃", CatalogSnapshot.from(result));

        assertThat(cache.getPlay("sili:鬼灭之刃").videos().get(0).url()).contains("m3u8");
        assertThat(cache.getCatalog("sili:鬼灭之刃").sources().get(0).episodes().get(0).title()).isEqualTo("第1集");
    }

    @Test
    void redisOutageStillServesFromCaffeineAndDoesNotThrow() {
        CrawlerCacheProperties properties = CrawlerCacheProperties.caffeineOnly();
        CaffeineCrawlerResultCache local = new CaffeineCrawlerResultCache(properties);
        AtomicInteger gets = new AtomicInteger();
        RemoteKvStore boom = explodingStore(gets);
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        TieredCrawlerResultCache cache = new TieredCrawlerResultCache(local, boom, properties, mapper);

        cache.putPlay("dm84:芙莉莲", sample());
        CrawlResourceResult hit = cache.getPlay("dm84:芙莉莲");
        assertThat(hit).isNotNull();
        assertThat(hit.videos()).hasSize(1);
        assertThat(gets.get()).isZero();
    }

    @Test
    void redisGetFailureFallsThroughToMissThenLocalPut() {
        CrawlerCacheProperties properties = CrawlerCacheProperties.caffeineOnly();
        CaffeineCrawlerResultCache local = new CaffeineCrawlerResultCache(properties);
        TieredCrawlerResultCache cache = new TieredCrawlerResultCache(
                local, explodingStore(new AtomicInteger()), properties, new ObjectMapper()
        );
        assertThat(cache.getPlay("missing")).isNull();
        cache.putPlay("dm84:芙莉莲", sample());
        assertThat(cache.getPlay("dm84:芙莉莲")).isNotNull();
    }

    @Test
    void redisKeysStayStable() {
        String lookup = CrawlCacheKeys.lookup("DM84", "  芙莉莲  第二季 ");
        assertThat(lookup).isEqualTo("dm84:芙莉莲 第二季");
        assertThat(CrawlCacheKeys.playRedisKey("acrawl", lookup)).isEqualTo("acrawl:play:dm84:芙莉莲 第二季");
        assertThat(CrawlCacheKeys.catalogRedisKey("acrawl", lookup)).isEqualTo("acrawl:catalog:dm84:芙莉莲 第二季");
    }

    @Test
    void secondInstanceHydratesFromRemoteWithoutRecrawlPayloadLoss() {
        CrawlerCacheProperties properties = CrawlerCacheProperties.caffeineOnly();
        MapRemoteKvStore remote = new MapRemoteKvStore();
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        TieredCrawlerResultCache writer = new TieredCrawlerResultCache(
                new CaffeineCrawlerResultCache(properties), remote, properties, mapper
        );
        writer.putPlay("dm84:芙莉莲", sample());
        writer.putCatalog("dm84:芙莉莲", CatalogSnapshot.from(sample()));

        TieredCrawlerResultCache reader = new TieredCrawlerResultCache(
                new CaffeineCrawlerResultCache(properties), remote, properties, mapper
        );
        CrawlResourceResult play = reader.getPlay("dm84:芙莉莲");
        CatalogSnapshot catalog = reader.getCatalog("dm84:芙莉莲");
        assertThat(play.videos().get(0).url()).isEqualTo("https://cdn.example.com/1.m3u8");
        assertThat(play.videos().get(0).roadName()).isEqualTo("线路1");
        assertThat(catalog.sources().get(0).episodes().get(0).title()).isEqualTo("第1集");
        assertThat(catalog.toString()).doesNotContain("cdn.example.com");
    }

    @Test
    void emptyVideosAreNotStoredAsPlay() {
        CaffeineCrawlerResultCache cache = new CaffeineCrawlerResultCache(CrawlerCacheProperties.caffeineOnly());
        cache.putPlay("dm84:空", CrawlResourceResult.failed("空", "DM84", "DM84", "没有"));
        assertThat(cache.getPlay("dm84:空")).isNull();
    }

    @Test
    void unreachableRedisDegradesToMissWithoutThrowing() {
        CrawlerCacheProperties.Redis redis = new CrawlerCacheProperties.Redis(
                true, "127.0.0.1", 1, 0, "", 200
        );
        LettuceRemoteKvStore store = new LettuceRemoteKvStore(redis);
        try {
            assertThat(store.get("acrawl:play:dm84:x")).isNull();
            store.set("acrawl:play:dm84:x", "{}", Duration.ofSeconds(10));
            assertThat(store.get("acrawl:play:dm84:x")).isNull();
        } finally {
            store.close();
        }
    }

    private static final class MapRemoteKvStore implements RemoteKvStore {
        private final Map<String, String> values = new ConcurrentHashMap<>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }
    }

    private static RemoteKvStore explodingStore(AtomicInteger gets) {
        return new RemoteKvStore() {
            @Override
            public String get(String key) {
                gets.incrementAndGet();
                throw new IllegalStateException("redis down");
            }

            @Override
            public void set(String key, String value, Duration ttl) {
                throw new IllegalStateException("redis down");
            }
        };
    }

    private static CrawlResourceResult sample() {
        return new CrawlResourceResult(
                "芙莉莲",
                "DM84",
                "DM84",
                List.of(new CrawlResourceResult.VideoResource(
                        "第1集",
                        "https://cdn.example.com/1.m3u8",
                        "https://example.com/play/1",
                        "线路1"
                )),
                List.of(),
                List.of()
        );
    }

    private static CrawlResourceResult signedPlay() {
        return new CrawlResourceResult(
                "鬼灭之刃",
                "SiliSili",
                "SiliSili",
                List.of(new CrawlResourceResult.VideoResource(
                        "第01集",
                        "https://silivideo.example.r2.cloudflarestorage.com/ep1.mp4?X-Amz-Signature=secret",
                        "https://www.silisili.link/play/1",
                        null
                )),
                List.of(),
                List.of()
        );
    }
}
