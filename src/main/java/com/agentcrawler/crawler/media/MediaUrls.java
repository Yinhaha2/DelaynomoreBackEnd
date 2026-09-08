package com.agentcrawler.crawler.media;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared media-URL harvest: extension match, {@code url=} wrappers, changeplay onclick.
 */
public final class MediaUrls {
    private static final Pattern MEDIA_EXT = Pattern.compile("(?i)\\.(m3u8|mp4)(\\?|$|#)");
    private static final Pattern CHANGE_PLAY = Pattern.compile(
            "(?i)changeplay\\s*\\(\\s*['\"]([^'\"]+)['\"]"
    );
    private static final Pattern HTTP = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    private MediaUrls() {}

    public static boolean looksLikeMedia(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return MEDIA_EXT.matcher(url).find()
                || lower.contains("mpegurl")
                || lower.contains("/m3u8");
    }

    public static void harvest(String candidate, Collection<String> sink) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        addIfMedia(candidate, sink);
        unwrapQuery(candidate, sink);
        Matcher changePlay = CHANGE_PLAY.matcher(candidate);
        while (changePlay.find()) {
            addIfMedia(stripYhdmSuffix(changePlay.group(1)), sink);
        }
    }

    public static Set<String> harvestAll(String text) {
        Set<String> urls = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return urls;
        }
        harvest(text, urls);
        Matcher http = HTTP.matcher(text);
        while (http.find()) {
            harvest(http.group(), urls);
        }
        return urls;
    }

    public static String stripYhdmSuffix(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.replace("changeplay('", "").replace("');", "").trim();
        int dollar = value.indexOf('$');
        if (dollar >= 0) {
            value = value.substring(0, dollar);
        }
        return value.trim();
    }

    public static boolean isVideoContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("video") || lower.contains("mpegurl");
    }

    private static void unwrapQuery(String candidate, Collection<String> sink) {
        try {
            URI uri = URI.create(candidate);
            if (uri.getRawQuery() == null) {
                return;
            }
            for (String part : uri.getRawQuery().split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                String decoded = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                    addIfMedia(decoded, sink);
                }
            }
        } catch (RuntimeException ignored) {
            // not a URL
        }
    }

    private static void addIfMedia(String url, Collection<String> sink) {
        if (looksLikeMedia(url)) {
            sink.add(url);
        }
    }
}
