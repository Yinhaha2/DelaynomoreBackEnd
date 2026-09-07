package com.agentcrawler.agent.langchain;

import com.agentcrawler.agent.session.SessionBlackboardService;
import com.agentcrawler.agent.session.SessionContextHolder;
import com.agentcrawler.config.AppProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.vision.AnimeVisionAnalysisResult;
import com.agentcrawler.vision.DeepSeekVisionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 视觉感知 Tool：内部调用 deepseek-v4-flash-vision-exp，结构化输出并自动锁定实体黑板。
 */
@Component
@ConditionalOnBean(DeepSeekVisionClient.class)
public class AnimeVisionTool {

    private final DeepSeekVisionClient visionClient;
    private final SessionBlackboardService blackboardService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public AnimeVisionTool(
            DeepSeekVisionClient visionClient,
            SessionBlackboardService blackboardService,
            AppProperties properties,
            ObjectMapper objectMapper
    ) {
        this.visionClient = visionClient;
        this.blackboardService = blackboardService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Tool("""
            分析动漫截图/表情包/截帧，识别作品名、角色、集数线索与视觉特征。
            当用户发送图片、询问出处/哪一集/是谁时必须先调用本工具。
            参数 imageUrl 必须是系统提供的 HTTP URL，禁止传入 base64。
            """)
    public String analyzeAnimeImage(
            @P("可访问的图片 URL，例如 http://localhost:8000/api/v1/files/images/img_xxx") String imageUrl
    ) {
        requireSessionId();
        AnimeVisionAnalysisResult result = visionClient.analyze(imageUrl);
        autoLockBlackboard(result);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.VISION_FAILED, "视觉结果序列化失败");
        }
    }

    private void autoLockBlackboard(AnimeVisionAnalysisResult result) {
        if (!result.hasWorkTitle()) {
            return;
        }
        if (result.getConfidence() < properties.vision().minConfidenceToLock()) {
            return;
        }
        String sessionId = SessionContextHolder.get();
        blackboardService.lockContext(
                sessionId,
                result.getWorkTitle(),
                result.getCharacters(),
                result.getEpisodeHint(),
                result.getSearchScene(),
                result.visualFeaturesSummary()
        );
    }

    private static String requireSessionId() {
        String sessionId = SessionContextHolder.get();
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("会话 ID 未绑定，无法执行视觉识别");
        }
        return sessionId;
    }
}
