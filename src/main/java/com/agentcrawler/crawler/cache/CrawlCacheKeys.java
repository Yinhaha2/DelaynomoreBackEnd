package com.agentcrawler.crawler.cache;

import java.util.Locale;

public final class CrawlCacheKeys {
    private CrawlCacheKeys() {}

    public static String lookup(String site, String keyword) {
        String resolvedSite = site == null ? "" : site.trim().toLowerCase(Locale.ROOT);
        String resolvedKeyword = normalizeKeyword(keyword);
        return resolvedSite + ":" + resolvedKeyword;
    }

    public static String playRedisKey(String prefix, String lookup) {
        return prefix + ":play:" + lookup;
    }

    public static String catalogRedisKey(String prefix, String lookup) {
        return prefix + ":catalog:" + lookup;
    }

    static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.trim().replaceAll("\\s+", " ");
    }
}
