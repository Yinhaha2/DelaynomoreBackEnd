package com.agentcrawler.config;

import com.agentcrawler.cache.BlobCrawlResultCache;
import com.agentcrawler.cache.CacheLookup;
import com.agentcrawler.cache.CosObjectBlobStore;
import com.agentcrawler.cache.CrawlResultCache;
import com.agentcrawler.cache.RevalidateSchedule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.HeadBucketRequest;
import com.qcloud.cos.region.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Clock;

@Configuration
public class CosCacheConfig {
    private static final Logger log = LoggerFactory.getLogger(CosCacheConfig.class);
    private static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024 * 1024;

    @Bean
    @Conditional(CosCacheCondition.class)
    CrawlResultCache crawlResultCache(Environment env, ObjectMapper objectMapper) {
        String secretId = env.getProperty("COS_SECRET_ID", "");
        String secretKey = env.getProperty("COS_SECRET_KEY", "");
        String region = env.getProperty("COS_REGION", "ap-guangzhou");
        String bucket = env.getProperty("COS_BUCKET", "");
        long maxBytes = parseMaxBytes(env.getProperty("COS_CACHE_MAX_BYTES"));

        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setConnectionTimeout(10_000);
        clientConfig.setSocketTimeout(20_000);
        COSClient client = new COSClient(credentials, clientConfig);
        long ttlSeconds = parseSeconds(env.getProperty("COS_REVALIDATE_TTL_SECONDS"), 7_200);
        long jitterSeconds = parseSeconds(env.getProperty("COS_REVALIDATE_JITTER_SECONDS"), 1_800);
        boolean available = probe(client, bucket, maxBytes, ttlSeconds, jitterSeconds);
        BlobCrawlResultCache store = new BlobCrawlResultCache(
                new CosObjectBlobStore(client, bucket),
                objectMapper,
                maxBytes,
                Clock.systemUTC(),
                RevalidateSchedule.of(ttlSeconds * 1000L, jitterSeconds * 1000L)
        );
        return new ManagedCrawlResultCache(client, store, available);
    }

    private static boolean probe(COSClient client, String bucket, long maxBytes, long ttlSeconds, long jitterSeconds) {
        try {
            client.headBucket(new HeadBucketRequest(bucket));
            log.info(
                    "检索缓存已连接 bucket={} maxBytes={} revalidateTtlSeconds={} jitterSeconds={}",
                    bucket,
                    maxBytes,
                    ttlSeconds,
                    jitterSeconds
            );
            return true;
        } catch (CosServiceException ex) {
            log.warn("检索缓存连接失败，将继续现爬 status={} errorCode={}", ex.getStatusCode(), ex.getErrorCode());
            return false;
        } catch (RuntimeException ex) {
            log.warn("检索缓存连接失败，将继续现爬: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private static long parseMaxBytes(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_BYTES;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : DEFAULT_MAX_BYTES;
        } catch (NumberFormatException ex) {
            return DEFAULT_MAX_BYTES;
        }
    }

    private static long parseSeconds(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value >= 0 ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static final class ManagedCrawlResultCache implements CrawlResultCache, DisposableBean {
        private final COSClient client;
        private final CrawlResultCache delegate;
        private final boolean available;

        private ManagedCrawlResultCache(COSClient client, CrawlResultCache delegate, boolean available) {
            this.client = client;
            this.delegate = delegate;
            this.available = available;
        }

        @Override
        public java.util.Optional<com.agentcrawler.crawler.model.CrawlResourceResult> find(String keyword, String site) {
            if (!available) {
                return java.util.Optional.empty();
            }
            return delegate.find(keyword, site);
        }

        @Override
        public void save(String keyword, String site, com.agentcrawler.crawler.model.CrawlResourceResult result) {
            if (!available) {
                return;
            }
            delegate.save(keyword, site, result);
        }

        @Override
        public java.util.Optional<CacheLookup> lookup(String keyword, String site) {
            if (!available) {
                return java.util.Optional.empty();
            }
            return delegate.lookup(keyword, site);
        }

        @Override
        public void invalidate(String keyword, String site) {
            if (!available) {
                return;
            }
            delegate.invalidate(keyword, site);
        }

        @Override
        public void postpone(String keyword, String site, boolean soon) {
            if (!available) {
                return;
            }
            delegate.postpone(keyword, site, soon);
        }

        @Override
        public void destroy() {
            client.shutdown();
        }
    }
}
