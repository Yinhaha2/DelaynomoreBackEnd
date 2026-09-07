package com.agentcrawler.link;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性前置提取：从用户文本中切出 HTTP(S) 与 magnet 链接。
 * 不依赖 LLM，避免 query 参数被吞字或分词截断。
 */
public final class LinkExtractor {

    private static final Pattern HTTP_URL = Pattern.compile(
            "(?i)(?:https?://|www\\.)[^\\s<>\"'`，。；！？、）】》\\[\\]{}]+"
    );
    private static final Pattern MAGNET = Pattern.compile(
            "(?i)magnet:\\?xt=urn:btih:[a-z0-9]+(?:&[^\\s<>\"'`，。；！？、）】》]+)*"
    );
    private static final String TRAILING_PUNCT = ".,;:!?)]}>'\"，。；：！？、）】》」』";

    private LinkExtractor() {
    }

    public static List<ExtractedLink> extract(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ExtractedLink> links = new ArrayList<>();

        Matcher magnetMatcher = MAGNET.matcher(message);
        while (magnetMatcher.find()) {
            String magnet = stripTrailingPunct(magnetMatcher.group());
            if (seen.add(magnet.toLowerCase(Locale.ROOT))) {
                links.add(new ExtractedLink(magnet, LinkKind.MAGNET));
            }
        }

        Matcher httpMatcher = HTTP_URL.matcher(message);
        while (httpMatcher.find()) {
            String url = normalizeHttp(stripTrailingPunct(httpMatcher.group()));
            if (url == null || !seen.add(url.toLowerCase(Locale.ROOT))) {
                continue;
            }
            links.add(new ExtractedLink(url, UrlClassifier.classify(url)));
        }
        return links;
    }

    public static List<String> extractRaw(String message) {
        List<String> raw = new ArrayList<>();
        for (ExtractedLink link : extract(message)) {
            raw.add(link.raw());
        }
        return raw;
    }

    static String stripTrailingPunct(String value) {
        int end = value.length();
        while (end > 0 && TRAILING_PUNCT.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    static String normalizeHttp(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("www.")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }
}
