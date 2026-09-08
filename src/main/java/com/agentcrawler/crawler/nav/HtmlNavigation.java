package com.agentcrawler.crawler.nav;

import com.agentcrawler.crawler.util.UrlNormalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows HTML interstitials that OkHttp {@code followRedirects} cannot see:
 * {@code meta http-equiv=refresh} and "verified successfully" hop pages.
 */
public final class HtmlNavigation {
    private static final Pattern META_URL = Pattern.compile(
            "(?i)url\\s*=\\s*['\"]?([^'\"\\s>]+)"
    );
    private static final String VERIFIED = "you have verified successfully";

    private HtmlNavigation() {}

    public static Optional<String> nextLocation(String html, String currentUrl) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Document document = Jsoup.parse(html, currentUrl == null ? "" : currentUrl);
        Optional<String> refresh = metaRefresh(document, currentUrl);
        if (refresh.isPresent()) {
            return refresh;
        }
        if (html.toLowerCase(Locale.ROOT).contains(VERIFIED)) {
            Element anchor = document.selectFirst("a[href]");
            if (anchor != null) {
                return resolve(currentUrl, anchor.attr("href"));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> metaRefresh(Document document, String currentUrl) {
        Element meta = document.selectFirst("meta[http-equiv=refresh]");
        if (meta == null) {
            return Optional.empty();
        }
        String content = meta.attr("content");
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = META_URL.matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return resolve(currentUrl, matcher.group(1).trim());
    }

    private static Optional<String> resolve(String currentUrl, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String resolved = UrlNormalizer.normalizeEpisodeUrl(
                currentUrl == null || currentUrl.isBlank() ? raw : currentUrl,
                raw
        );
        if (resolved.isBlank() || resolved.equals(currentUrl)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }
}
