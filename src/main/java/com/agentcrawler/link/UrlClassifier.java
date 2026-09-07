package com.agentcrawler.link;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class UrlClassifier {

    private static final Set<String> SHORT_HOSTS = Set.of(
            "b23.tv", "b23.wtf", "t.cn", "bit.ly", "tinyurl.com", "shorturl.at", "j.mp"
    );
    private static final Set<String> VIDEO_HOST_MARKERS = Set.of(
            "bilibili.com", "b23.tv", "youtube.com", "youtu.be",
            "gamer.com.tw", "acfun.cn", "iqiyi.com", "youku.com"
    );

    private UrlClassifier() {
    }

    public static LinkKind classify(String url) {
        if (url == null || url.isBlank()) {
            return LinkKind.WEB_PAGE;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("magnet:")) {
            return LinkKind.MAGNET;
        }
        if (isStreamPath(lower)) {
            return LinkKind.STREAM;
        }
        String host = hostOf(url);
        if (host.isBlank()) {
            return LinkKind.WEB_PAGE;
        }
        if (SHORT_HOSTS.contains(host) || SHORT_HOSTS.stream().anyMatch(host::endsWith)) {
            return LinkKind.SHORT_LINK;
        }
        if (VIDEO_HOST_MARKERS.stream().anyMatch(host::contains)) {
            return LinkKind.VIDEO_SITE;
        }
        return LinkKind.WEB_PAGE;
    }

    public static boolean isShortHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return SHORT_HOSTS.contains(lower) || SHORT_HOSTS.stream().anyMatch(lower::endsWith);
    }

    public static boolean isVideoHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return VIDEO_HOST_MARKERS.stream().anyMatch(lower::contains);
    }

    static boolean isStreamPath(String lowerUrl) {
        String path = lowerUrl.split("[?#]", 2)[0];
        return path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".flv") || path.endsWith(".webm");
    }

    static String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return "";
        }
    }
}
