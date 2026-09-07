package com.agentcrawler.link;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoSiteParser {

    private static final Pattern BILIBILI_BV = Pattern.compile("(?i)\\b(BV[0-9A-Za-z]{10})\\b");
    private static final Pattern BILIBILI_AV = Pattern.compile("(?i)\\b(av\\d+)\\b");
    private static final Pattern YOUTUBE_ID = Pattern.compile("(?i)(?:youtu\\.be/|v=)([A-Za-z0-9_-]{6,})");
    private static final Pattern BANGUMI_SUBJECT = Pattern.compile("(?i)bangumi\\.tv/subject/(\\d+)");

    private VideoSiteParser() {
    }

    public static VideoSiteHint parse(String url) {
        if (url == null || url.isBlank()) {
            return VideoSiteHint.empty();
        }
        String host = UrlClassifier.hostOf(url);
        Matcher bv = BILIBILI_BV.matcher(url);
        if (bv.find()) {
            return new VideoSiteHint("bilibili", bv.group(1), host);
        }
        Matcher av = BILIBILI_AV.matcher(url);
        if (av.find() && host.contains("bilibili")) {
            return new VideoSiteHint("bilibili", av.group(1), host);
        }
        Matcher yt = YOUTUBE_ID.matcher(url);
        if (yt.find() && (host.contains("youtube") || host.contains("youtu.be"))) {
            return new VideoSiteHint("youtube", yt.group(1), host);
        }
        Matcher bangumi = BANGUMI_SUBJECT.matcher(url);
        if (bangumi.find()) {
            return new VideoSiteHint("bangumi", bangumi.group(1), host);
        }
        return new VideoSiteHint(guessSite(host), "", host);
    }

    private static String guessSite(String host) {
        if (host.contains("bilibili") || host.contains("b23.tv")) {
            return "bilibili";
        }
        if (host.contains("youtube") || host.contains("youtu.be")) {
            return "youtube";
        }
        if (host.contains("bangumi")) {
            return "bangumi";
        }
        if (host.contains("gamer.com.tw")) {
            return "bahamut";
        }
        return host;
    }

    public static String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return "";
        }
    }

    public record VideoSiteHint(String site, String mediaId, String host) {
        static VideoSiteHint empty() {
            return new VideoSiteHint("", "", "");
        }

        public boolean hasMediaId() {
            return mediaId != null && !mediaId.isBlank();
        }
    }
}
