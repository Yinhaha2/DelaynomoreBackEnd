package com.agentcrawler.agent.langchain;

import com.agentcrawler.agent.session.SessionContextHolder;
import com.agentcrawler.link.LinkInspectorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 链接嗅探 Tool：解析网页/短链/磁力/直链，结构化返回并自动锁定实体黑板。
 */
@Component
public class LinkInspectorTool {

    private final LinkInspectorService linkInspectorService;
    private final ObjectMapper objectMapper;

    public LinkInspectorTool(LinkInspectorService linkInspectorService, ObjectMapper objectMapper) {
        this.linkInspectorService = linkInspectorService;
        this.objectMapper = objectMapper;
    }

    @Tool("""
            解析用户给出的网页 URL、视频站链接、短链、磁力链或 m3u8/mp4 直链。
            返回标题、作品名、集数线索、站点 ID 等结构化信息。
            用户消息中的链接通常已由系统前置解析；仅当需要补充解析某个具体 URL 时再调用。
            """)
    public String inspectLink(@P("完整 URL 或 magnet 链接，必须原样传入，禁止截断 query") String url) {
        String sessionId = requireSessionId();
        try {
            return objectMapper.writeValueAsString(linkInspectorService.inspectAndLock(sessionId, url));
        } catch (Exception ex) {
            return "{\"error\":\"" + ex.getMessage() + "\"}";
        }
    }

    private static String requireSessionId() {
        String sessionId = SessionContextHolder.get();
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("会话 ID 未绑定，无法解析链接");
        }
        return sessionId;
    }
}
