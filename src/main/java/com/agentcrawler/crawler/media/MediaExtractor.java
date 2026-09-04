package com.agentcrawler.crawler.media;

import com.agentcrawler.crawler.util.UrlNormalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MediaExtractor {
    private static final Pattern MEDIA_IN_TEXT = Pattern.compile(
            "(https?://[^\\s\"'<>]+?\\.(?:m3u8|mp4))|(https?://[^\\s\"'<>]+?/[^\\s\"'<>]*m3u8[^\\s\"'<>]*)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IFRAME_SRC = Pattern.compile(
            "<iframe[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    public List<String> extractVideoUrls(String html, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        collectRegexMatches(html, urls);
        Document document = Jsoup.parse(html, baseUrl);
        for (Element video : document.select("video[src], video source[src]")) {
            addIfPresent(urls, video.attr("abs:src"));
        }
        for (Element iframe : document.select("iframe[src]")) {
            String iframeUrl = iframe.attr("abs:src");
            addIfPresent(urls, iframeUrl);
            collectRegexMatches(iframeUrl, urls);
            decodeEmbeddedMedia(iframeUrl, urls);
        }
        return new ArrayList<>(urls);
    }

    public List<String> extractImageUrls(String html, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        Document document = Jsoup.parse(html, baseUrl);
        Elements images = document.select("img[src]");
        for (Element image : images) {
            addIfPresent(urls, image.attr("abs:src"));
        }
        return new ArrayList<>(urls);
    }

    public List<String> extractPageLinks(String html, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        Document document = Jsoup.parse(html, baseUrl);
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.attr("abs:href");
            if (!href.isBlank() && !href.startsWith("javascript:")) {
                urls.add(href);
            }
        }
        return new ArrayList<>(urls);
    }

    private void decodeEmbeddedMedia(String iframeUrl, Set<String> urls) {
        URI uri = URI.create(iframeUrl);
        if (uri.getRawQuery() == null) {
            return;
        }
        for (String part : uri.getRawQuery().split("&")) {
            collectRegexMatches(part, urls);
        }
    }

    private void collectRegexMatches(String text, Set<String> urls) {
        Matcher matcher = MEDIA_IN_TEXT.matcher(text);
        while (matcher.find()) {
            for (int group = 1; group <= matcher.groupCount(); group++) {
                String value = matcher.group(group);
                if (value != null && !value.isBlank()) {
                    urls.add(value.trim());
                }
            }
        }
    }

    private void addIfPresent(Set<String> urls, String value) {
        if (value != null && !value.isBlank()) {
            urls.add(UrlNormalizer.normalizeEpisodeUrl(value, value));
        }
    }
}
