package org.example.schoolshop.common;

import org.example.schoolshop.config.SchoolShopProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private final SchoolShopProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitService(SchoolShopProperties properties) {
        this.properties = properties;
    }

    public void checkWrite(long userId, String action) {
        int limit = properties.getSecurity().getRateLimitPerMinute();
        if (limit <= 0) {
            return;
        }
        String key = userId + ":" + action;
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.computeIfAbsent(key, k -> new Window(minute));
        synchronized (window) {
            if (window.minute != minute) {
                window.minute = minute;
                window.count.set(0);
            }
            if (window.count.incrementAndGet() > limit) {
                throw BizException.of(429, "操作过于频繁，请稍后再试");
            }
        }
    }

    private static class Window {
        long minute;
        final AtomicInteger count = new AtomicInteger();

        Window(long minute) {
            this.minute = minute;
        }
    }
}
