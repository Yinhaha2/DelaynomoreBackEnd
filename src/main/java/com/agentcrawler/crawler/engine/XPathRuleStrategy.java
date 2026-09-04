package com.agentcrawler.crawler.engine;

import cn.wanghaomiao.xpath.model.JXDocument;
import cn.wanghaomiao.xpath.model.JXNode;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.crawler.util.TemplateRenderer;
import com.agentcrawler.crawler.util.UrlNormalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class XPathRuleStrategy {
    public String buildSearchUrl(PluginRule rule, String keyword) {
        return TemplateRenderer.renderEncoded(rule.getSearchURL(), Map.of("keyword", keyword));
    }

    public String buildChapterUrl(PluginRule rule, String source) {
        return UrlNormalizer.normalizeEpisodeUrl(rule.getBaseURL(), source);
    }

    public List<SearchItem> parseSearch(String html, PluginRule rule) {
        JXDocument document = new JXDocument(html);
        List<JXNode> nodes = document.selN(rule.getSearchList());
        List<SearchItem> items = new ArrayList<>();
        for (JXNode node : nodes) {
            String nodeHtml = node.toString();
            JXDocument fragment = new JXDocument(nodeHtml);
            String name = firstText(fragment, rule.getSearchName());
            String source = firstHref(fragment, rule.getSearchResult());
            String imageUrl = rule.getSearchImage() == null || rule.getSearchImage().isBlank()
                    ? ""
                    : firstAttr(fragment, rule.getSearchImage(), "src");
            if (name.isBlank() || source.isBlank()) {
                continue;
            }
            items.add(new SearchItem(
                    name,
                    UrlNormalizer.normalizeEpisodeUrl(rule.getBaseURL(), source),
                    imageUrl.isBlank()
                            ? ""
                            : UrlNormalizer.normalizeEpisodeUrl(rule.getBaseURL(), imageUrl)
            ));
        }
        return items;
    }

    public List<Road> parseChapters(String html, PluginRule rule) {
        JXDocument document = new JXDocument(html);
        List<JXNode> roadNodes = document.selN(rule.getChapterRoads());
        List<Road> roads = new ArrayList<>();
        for (int index = 0; index < roadNodes.size(); index++) {
            JXNode roadNode = roadNodes.get(index);
            JXDocument fragment = new JXDocument(roadNode.toString());
            List<JXNode> episodeNodes = fragment.selN(rule.getChapterResult());
            List<String> names = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            for (JXNode episodeNode : episodeNodes) {
                Element element = Jsoup.parse(episodeNode.toString()).body().children().first();
                if (element == null) {
                    continue;
                }
                String episodeName = element.text().trim();
                String href = element.attr("href").trim();
                if (episodeName.isBlank() || href.isBlank()) {
                    continue;
                }
                names.add(episodeName);
                urls.add(UrlNormalizer.normalizeEpisodeUrl(rule.getBaseURL(), href));
            }
            if (!urls.isEmpty()) {
                roads.add(new Road("线路" + (index + 1), names, urls));
            }
        }
        return roads;
    }

    private String firstText(JXDocument fragment, String xpath) {
        List<JXNode> nodes = fragment.selN(xpath);
        if (nodes.isEmpty()) {
            return "";
        }
        return Jsoup.parse(nodes.get(0).toString()).text().trim();
    }

    private String firstHref(JXDocument fragment, String xpath) {
        return firstAttr(fragment, xpath, "href");
    }

    private String firstAttr(JXDocument fragment, String xpath, String attr) {
        List<JXNode> nodes = fragment.selN(xpath);
        if (nodes.isEmpty()) {
            return "";
        }
        Element element = Jsoup.parse(nodes.get(0).toString()).body().children().first();
        return element == null ? "" : element.attr(attr).trim();
    }
}
