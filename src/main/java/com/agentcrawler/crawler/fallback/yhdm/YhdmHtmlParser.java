package com.agentcrawler.crawler.fallback.yhdm;

import com.agentcrawler.crawler.media.MediaUrls;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.crawler.util.UrlNormalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSS selectors taken from SakuraAnime {@code YhdmJsoupUtils} (YHDM layout).
 */
public final class YhdmHtmlParser {
    private static final Pattern HTTP_IN_TEXT = Pattern.compile("https?://[^\\s\"')]+", Pattern.CASE_INSENSITIVE);

    private YhdmHtmlParser() {}

    public static List<SearchItem> parseSearch(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        List<SearchItem> items = new ArrayList<>();
        for (Element li : document.select("div.lpic > ul > li")) {
            String name = li.select("h2").text();
            String href = li.select("h2 > a").attr("href");
            String img = firstImage(li.select("img").first());
            if (name.isBlank() || href.isBlank()) {
                continue;
            }
            items.add(new SearchItem(
                    name.trim(),
                    UrlNormalizer.normalizeEpisodeUrl(baseUrl, href),
                    img.isBlank() ? "" : UrlNormalizer.normalizeEpisodeUrl(baseUrl, img)
            ));
        }
        return items;
    }

    public static List<Road> parseChapters(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        Elements drama = document.select("div.movurl > ul > li");
        if (drama.isEmpty()) {
            drama = document.select("div.movurls > ul > li");
        }
        List<String> names = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (Element li : drama) {
            Element a = li.selectFirst("a");
            if (a == null) {
                continue;
            }
            String name = a.text().trim();
            String href = a.attr("href").trim();
            if (name.isBlank() || href.isBlank()) {
                continue;
            }
            names.add(name);
            urls.add(UrlNormalizer.normalizeEpisodeUrl(baseUrl, href));
        }
        if (urls.isEmpty()) {
            return List.of();
        }
        return List.of(new Road("默认播放列表", names, urls));
    }

    public static List<String> parsePlayUrls(String html, String playUrlTemplate) {
        Document document = Jsoup.parse(html);
        Set<String> urls = new LinkedHashSet<>();
        for (Element a : document.select("div.playbo > a")) {
            addPlayCandidate(MediaUrls.stripYhdmSuffix(a.attr("onClick")), playUrlTemplate, urls);
            addPlayCandidate(MediaUrls.stripYhdmSuffix(a.attr("onclick")), playUrlTemplate, urls);
        }
        urls.addAll(MediaUrls.harvestAll(html));
        return new ArrayList<>(urls);
    }

    public static String firstImage(Element image) {
        if (image == null) {
            return "";
        }
        for (String attr : List.of("src", "data-src", "data-original", "data-url", "data-lazy")) {
            String value = image.attr(attr);
            if (value != null && !value.isBlank() && !value.startsWith("data:")) {
                return value.trim();
            }
        }
        Matcher matcher = HTTP_IN_TEXT.matcher(image.attr("style") + " " + image.attr("srcset"));
        return matcher.find() ? matcher.group() : "";
    }

    private static void addPlayCandidate(String raw, String playUrlTemplate, Set<String> urls) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            urls.add(raw);
            return;
        }
        if (playUrlTemplate != null && playUrlTemplate.contains("%s")) {
            urls.add(String.format(playUrlTemplate, raw));
        }
    }
}
