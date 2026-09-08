package com.agentcrawler.crawler.fallback.silisili;

import com.agentcrawler.crawler.fallback.yhdm.YhdmHtmlParser;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.crawler.util.UrlNormalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSS selectors taken from SakuraAnime {@code ImomoeJsoupUtils} (SiliSili layout).
 */
public final class SilisiliHtmlParser {
    private static final Pattern HTTP_IN_TEXT = Pattern.compile("https?://[^\\s\"')]+", Pattern.CASE_INSENSITIVE);

    private SilisiliHtmlParser() {}

    public static List<SearchItem> parseSearch(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        List<SearchItem> items = new ArrayList<>();
        for (Element article : document.select("article.post-list")) {
            Element cover = article.selectFirst("div.search-image a");
            if (cover == null) {
                continue;
            }
            String name = cover.attr("title");
            String href = cover.attr("href");
            String img = imageFrom(cover.selectFirst("img"));
            if (name.isBlank()) {
                name = article.select("div.entry-summary").text();
            }
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
        Elements boxes = document.select("div.play-pannel-box");
        if (boxes.isEmpty()) {
            return parsePlaylistFallback(document, baseUrl);
        }
        List<Road> roads = new ArrayList<>();
        int index = 0;
        for (Element box : boxes) {
            String title = box.select("div.widget-title").text();
            String tip = box.select("span.pull-right").text();
            if (title.contains("下载")) {
                continue;
            }
            if (!tip.isBlank()) {
                title = title + " [" + tip + "]";
            }
            if (title.isBlank()) {
                index += 1;
                title = "线路" + index;
            }
            List<String> names = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            for (Element a : box.select("ul > li > a")) {
                String name = a.text().trim();
                String href = a.attr("href").trim();
                if (name.isBlank() || href.isBlank()) {
                    continue;
                }
                names.add(name);
                urls.add(UrlNormalizer.normalizeEpisodeUrl(baseUrl, href));
            }
            if (!urls.isEmpty()) {
                roads.add(new Road(title, names, urls));
            }
        }
        return roads;
    }

    private static List<Road> parsePlaylistFallback(Document document, String baseUrl) {
        Elements playlists = document.select("ul.playlist");
        if (playlists.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (Element a : playlists.first().select("a")) {
            String name = a.text().trim();
            String href = a.attr("href").trim();
            if (name.isBlank() || href.isBlank()) {
                continue;
            }
            names.add(name);
            urls.add(UrlNormalizer.normalizeEpisodeUrl(baseUrl, href));
        }
        return urls.isEmpty() ? List.of() : List.of(new Road("线路1", names, urls));
    }

    private static String imageFrom(Element image) {
        String direct = YhdmHtmlParser.firstImage(image);
        if (!direct.isBlank()) {
            return direct;
        }
        if (image == null) {
            return "";
        }
        Matcher matcher = HTTP_IN_TEXT.matcher(image.attr("srcset") + " " + image.attr("style"));
        return matcher.find() ? matcher.group() : "";
    }
}
