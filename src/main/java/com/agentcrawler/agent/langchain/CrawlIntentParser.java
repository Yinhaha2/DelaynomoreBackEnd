package com.agentcrawler.agent.langchain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrawlIntentParser {
    private static final Pattern QUOTED_TITLE = Pattern.compile("《([^》]+)》");
    private static final Pattern SEARCH_PREFIX = Pattern.compile("(?:帮我|请|麻烦)?(?:找|搜|搜索|爬取|检索)(.+?)(?:的(?:播放)?资源|播放链接|链接|资源|$)");
    private static final Pattern SITE_PLUGIN = Pattern.compile("(?i)\\b(DM84|[A-Za-z0-9_-]{2,20})\\b");
    private static final Pattern SITE_URL = Pattern.compile("(https?://[^\\s]+)");

    private CrawlIntentParser() {}

    public static Optional<CrawlIntent> parse(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String keyword = extractKeyword(message);
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        String site = extractSite(message);
        return Optional.of(new CrawlIntent(keyword.trim(), site == null ? "DM84" : site.trim()));
    }

    private static String extractKeyword(String message) {
        Matcher quoted = QUOTED_TITLE.matcher(message);
        if (quoted.find()) {
            return quoted.group(1);
        }
        Matcher prefix = SEARCH_PREFIX.matcher(message);
        if (prefix.find()) {
            return cleanupKeyword(prefix.group(1));
        }
        return null;
    }

    private static String extractSite(String message) {
        Matcher urlMatcher = SITE_URL.matcher(message);
        if (urlMatcher.find()) {
            return urlMatcher.group(1);
        }
        Matcher pluginMatcher = SITE_PLUGIN.matcher(message);
        while (pluginMatcher.find()) {
            String candidate = pluginMatcher.group(1);
            if (!candidate.equalsIgnoreCase("http") && !candidate.equalsIgnoreCase("https")) {
                return candidate;
            }
        }
        return null;
    }

    private static String cleanupKeyword(String raw) {
        return raw.replaceAll("[：:，,。！？!?]+$", "").trim();
    }

    public record CrawlIntent(String keyword, String site) {}
}
