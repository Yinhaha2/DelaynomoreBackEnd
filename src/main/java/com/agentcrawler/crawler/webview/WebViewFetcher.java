package com.agentcrawler.crawler.webview;

import java.util.Map;

/**
 * Optional browser-backed fetch for plugin rules with {@code useWebview: true}.
 */
public interface WebViewFetcher {

    boolean available();

    FetchedPage fetch(String url);

    default FetchedPage fetch(String url, Map<String, String> extraHeaders) {
        return fetch(url);
    }
}
