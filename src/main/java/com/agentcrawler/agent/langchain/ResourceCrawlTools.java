package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class ResourceCrawlTools {
    private final ResourceCrawlerService crawlerService;
    private final ObjectMapper objectMapper;

    public ResourceCrawlTools(ResourceCrawlerService crawlerService, ObjectMapper objectMapper) {
        this.crawlerService = crawlerService;
        this.objectMapper = objectMapper;
    }

    @Tool("""
            在指定站点按关键词定向检索动漫/影视的视频、链接与图片资源。
            keyword 为搜索关键词（番剧名、季数等），site 为站点插件名（如 DM84）或站点 baseURL。
            用户未指定站点时使用 DM84。
            """)
    public String searchResources(
            @P("搜索关键词，例如：咒术回战 第二季") String keyword,
            @P("目标站点插件名或 baseURL，默认 DM84") String site
    ) {
        String resolvedSite = site == null || site.isBlank() ? "DM84" : site.trim();
        CrawlResourceResult result = crawlerService.crawl(keyword.trim(), resolvedSite);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"error\":\"" + ex.getMessage() + "\"}";
        }
    }

    @Tool("列出当前可用的爬虫站点插件名称")
    public String listAvailableSites() {
        return crawlerService.availableSites();
    }
}
