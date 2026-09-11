package com.agentcrawler.crawler.reliability;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Coalesce in-flight crawls for the same site+keyword. Not a cache: the map entry is removed when the first call finishes.
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
