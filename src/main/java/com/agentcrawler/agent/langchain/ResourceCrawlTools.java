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
            在指定站点按关键词定向检索视频/链接/图片资源。
            参数 keyword 为搜索关键词（如番剧名），site 为站点插件名（如 DM84）或站点 baseURL。
            """)
    public String crawlResources(
            @P("搜索关键词，例如：葬送的芙莉莲") String keyword,
            @P("目标站点插件名或 baseURL，例如：DM84") String site
    ) {
        CrawlResourceResult result = crawlerService.crawl(keyword, site);
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
