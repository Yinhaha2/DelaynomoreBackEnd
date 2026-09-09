package com.agentcrawler.agent.langchain;

import com.agentcrawler.agent.session.SessionContextHolder;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceCrawlTools {
    private static final Logger log = LoggerFactory.getLogger(ResourceCrawlTools.class);

    private final ResourceCrawlerService crawlerService;
    private final ObjectMapper objectMapper;

    public ResourceCrawlTools(ResourceCrawlerService crawlerService, ObjectMapper objectMapper) {
        this.crawlerService = crawlerService;
        this.objectMapper = objectMapper;
    }

    @Tool("""
            在指定站点按关键词定向检索动漫/影视的视频、链接与图片资源。
            keyword 必须是用户当前要找的作品名，禁止擅自换成其他作品。
            site 为站点插件名（如 DM84、YHDM、SiliSili）。用户未指定时传 DM84。
            插件站点拿不到播放地址时，后端会自动降级到樱花动漫 / SiliSili，无需再换 site 重试。
            成功时只返回精炼摘要（作品名、线路数、线路名、更新集数），不含任何播放 URL。
            完整选集已由系统直接推给前端卡片，你只需用 2～3 句中文做观影推荐，禁止复述或编造链接。
            本工具不会抛异常：失败时 JSON 里会有 error 字段，请据此用一两句中文告知用户，不要朗读 URL 或 HTTP 状态码。
            """)
    public String searchResources(
            @P("搜索关键词，例如：咒术回战 第二季") String keyword,
            @P("目标站点插件名或 baseURL，默认 DM84") String site
    ) {
        String resolvedKeyword = keyword == null ? "" : keyword.trim();
        String resolvedSite = site == null || site.isBlank() ? "DM84" : site.trim();
        CrawlResourceResult result;
        try {
            result = crawlerService.crawl(resolvedKeyword, resolvedSite);
        } catch (Exception ex) {
            log.warn("searchResources 失败: {}", ex.toString());
            result = CrawlResourceResult.failed(
                    resolvedKeyword,
                    resolvedSite,
                    resolvedSite,
                    ResourceCrawlerService.userFacingMessage(ex)
            );
        }
        CrawlResultBuffer.push(SessionContextHolder.get(), result);
        try {
            return objectMapper.writeValueAsString(CrawlToolSummary.from(result));
        } catch (Exception ex) {
            return "{\"ok\":false,\"title\":\"" + resolvedKeyword + "\",\"error\":\"检索结果序列化失败\"}";
        }
    }

    @Tool("列出当前可用的爬虫站点插件名称。仅当用户明确询问有哪些站点时才调用，不要在检索失败后自动调用。")
    public String listAvailableSites() {
        return crawlerService.availableSites();
    }
}
