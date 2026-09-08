package com.agentcrawler.crawler.webview;

import java.util.List;

/**
 * HTML plus media URLs captured from a browser session (m3u8 / mp4 / player_aaaa).
 */
public record FetchedPage(String html, List<String> mediaUrls) {

    public FetchedPage {
        mediaUrls = mediaUrls == null ? List.of() : List.copyOf(mediaUrls);
    }

    public static FetchedPage htmlOnly(String html) {
        return new FetchedPage(html, List.of());
    }
}
