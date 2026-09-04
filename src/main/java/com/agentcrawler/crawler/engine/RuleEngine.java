package com.agentcrawler.crawler.engine;

import com.agentcrawler.crawler.http.SiteHttpClient;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuleEngine {
    private final SiteHttpClient httpClient;
    private final XPathRuleStrategy xpathRuleStrategy;

    public RuleEngine(SiteHttpClient httpClient, XPathRuleStrategy xpathRuleStrategy) {
        this.httpClient = httpClient;
        this.xpathRuleStrategy = xpathRuleStrategy;
    }

    public List<SearchItem> search(PluginRule rule, String keyword) {
        try {
            String raw = fetchSearch(rule, keyword);
            List<SearchItem> items = xpathRuleStrategy.parseSearch(raw, rule);
            if (items.isEmpty()) {
                throw new AppException(ErrorCode.CRAWL_FAILED, "站点 " + rule.getName() + " 未找到匹配结果");
            }
            return items;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.CRAWL_FAILED, "搜索失败: " + ex.getMessage(), ex);
        }
    }

    public List<Road> queryChapters(PluginRule rule, String source) {
        try {
            String chapterUrl = xpathRuleStrategy.buildChapterUrl(rule, source);
            String raw = httpClient.getText(chapterUrl, buildHeaders(rule, chapterUrl, false));
            List<Road> roads = xpathRuleStrategy.parseChapters(raw, rule);
            if (roads.isEmpty()) {
                throw new AppException(ErrorCode.CRAWL_FAILED, "站点 " + rule.getName() + " 未解析到剧集线路");
            }
            return roads;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.CRAWL_FAILED, "章节解析失败: " + ex.getMessage(), ex);
        }
    }

    public String fetchPage(PluginRule rule, String url) throws IOException {
        return httpClient.getText(url, buildHeaders(rule, url, false));
    }

    private String fetchSearch(PluginRule rule, String keyword) throws IOException {
        String searchUrl = xpathRuleStrategy.buildSearchUrl(rule, keyword);
        Map<String, String> headers = buildHeaders(rule, searchUrl, true);
        if (rule.isUsePost()) {
            return httpClient.postForm(
                    SiteHttpClient.stripQuery(searchUrl),
                    SiteHttpClient.parseQuery(searchUrl),
                    headers
            );
        }
        return httpClient.getText(searchUrl, headers);
    }

    private Map<String, String> buildHeaders(PluginRule rule, String url, boolean includeReferer) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/json,*/*");
        if (includeReferer) {
            headers.put("Referer", rule.getReferer().isBlank() ? rule.getBaseURL() + "/" : rule.getReferer());
        }
        if (rule.getUserAgent() != null && !rule.getUserAgent().isBlank()) {
            headers.put("User-Agent", rule.getUserAgent());
        } else {
            headers.put(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            );
        }
        return headers;
    }
}
