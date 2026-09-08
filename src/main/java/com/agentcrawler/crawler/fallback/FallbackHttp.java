package com.agentcrawler.crawler.fallback;

import com.agentcrawler.crawler.http.SiteHttpClient;
import com.agentcrawler.crawler.nav.HtmlNavigation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FallbackHttp {
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private FallbackHttp() {}

    public static Map<String, String> headers(String baseUrl, String cookie) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "text/html,application/json,*/*");
        headers.put("User-Agent", DEFAULT_UA);
        if (baseUrl != null && !baseUrl.isBlank()) {
            headers.put("Referer", baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
            headers.put("Origin", trimSlash(baseUrl));
        }
        if (cookie != null && !cookie.isBlank()) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    public static String getFollowingHops(SiteHttpClient http, String url, Map<String, String> headers) throws IOException {
        String current = url;
        String html = http.getText(current, headers);
        for (int hop = 0; hop < 5; hop++) {
            var next = HtmlNavigation.nextLocation(html, current);
            if (next.isEmpty()) {
                return html;
            }
            current = next.get();
            html = http.getText(current, headers);
        }
        return html;
    }

    public static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
