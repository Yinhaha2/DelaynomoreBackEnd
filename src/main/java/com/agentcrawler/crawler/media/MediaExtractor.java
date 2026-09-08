package com.agentcrawler.crawler.media;

import com.agentcrawler.crawler.util.UrlNormalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MediaExtractor {
    private static final Pattern HTTP_IN_TEXT = Pattern.compile("https?://[^\\s\"')]+", Pattern.CASE_INSENSITIVE);

    public List<String> extractVideoUrls(String html, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        MediaUrls.harvestAll(html).forEach(urls::add);
        Document document = Jsoup.parse(html, baseUrl);
        for (Element video : document.select("video[src], video source[src]")) {
            String src = video.attr("abs:src");
            if (src != null && !src.isBlank()) {
                urls.add(src);
            }
        }
        for (Element iframe : document.select("iframe[src]")) {
            MediaUrls.harvest(iframe.attr("abs:src"), urls);
        }
        for (Element clickable : document.select("[onclick], [onClick]")) {
            MediaUrls.harvest(clickable.attr("onclick") + clickable.attr("onClick"), urls);
        }
        return new ArrayList<>(urls);
    }

    public List<String> extractImageUrls(String html, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        Document document = Jsoup.parse(html, baseUrl);
        Elements images = document.select("img");
        for (Element image : images) {
            String found = firstImageAttr(image);
            if (found.isBlank()) {
                continue;
            }
            String absolute = found.startsWith("http")
                    ? found
                    : UrlNormalizer.normalizeEpisodeUrl(baseUrl, found);
            if (absolute != null && !absolute.isBlank()) {
                urls.add(absolute);
            }
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

    private static String firstImageAttr(Element image) {
        for (String attr : List.of("abs:src", "src", "data-src", "data-original", "data-url", "data-lazy")) {
            String value = image.attr(attr);
            if (value != null && !value.isBlank() && !value.startsWith("data:")) {
                return value.trim();
            }
        }
        Matcher matcher = HTTP_IN_TEXT.matcher(image.attr("style") + " " + image.attr("srcset"));
        return matcher.find() ? matcher.group() : "";
    }
}
