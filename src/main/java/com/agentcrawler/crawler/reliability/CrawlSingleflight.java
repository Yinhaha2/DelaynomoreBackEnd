package com.agentcrawler.crawler.reliability;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * 同一 site+keyword 的并发检索合并成一次上游调用，避免缓存未命中时打爆同一站点。
 * 这不是缓存：调用结束就从 map 摘掉，下一次请求会重新爬。失败会传给等待方，不会被记成成功结果。
 */
public class CrawlSingleflight {
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inflight = new ConcurrentHashMap<>();

    public static String key(String site, String keyword) {
        String resolvedSite = site == null ? "" : site.trim().toLowerCase();
        String resolvedKeyword = keyword == null ? "" : keyword.trim();
        return resolvedSite + "\0" + resolvedKeyword;
    }

    public static CrawlSingleflight direct() {
        return new CrawlSingleflight() {
            @Override
            public <T> T run(String key, Supplier<T> supplier) {
                return supplier.get();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public <T> T run(String key, Supplier<T> supplier) {
        if (key == null || key.isBlank()) {
            return supplier.get();
        }
        CompletableFuture<Object> created = new CompletableFuture<>();
        // 只有第一个调用方执行 supplier，后来者只 join，避免同一关键词并发打同一站点。
        CompletableFuture<Object> existing = inflight.putIfAbsent(key, created);
        if (existing != null) {
            try {
                return (T) existing.join();
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(cause);
            }
        }
        try {
            T result = supplier.get();
            created.complete(result);
            return result;
        } catch (RuntimeException ex) {
            created.completeExceptionally(ex);
            throw ex;
        } catch (Exception ex) {
            created.completeExceptionally(ex);
            throw new IllegalStateException(ex);
        } finally {
            inflight.remove(key, created);
        }
    }
}
