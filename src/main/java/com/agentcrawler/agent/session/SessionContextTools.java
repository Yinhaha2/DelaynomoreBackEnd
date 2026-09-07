package com.agentcrawler.agent.session;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 可调用的会话黑板工具：显式锁定/读取/清除实体锚点。
 */
@Component
public class SessionContextTools {

    private final SessionBlackboardService blackboardService;

    public SessionContextTools(SessionBlackboardService blackboardService) {
        this.blackboardService = blackboardService;
    }

    @Tool("锁定当前会话的检索领域（作品名、角色、集数进度等）。识图或定位成功后应调用，防止后续多轮对话注意力漂移。")
    public String lockSessionContext(
            @P("锁定的作品名，如 鬼灭之刃 游郭篇") String workTitle,
            @P("锁定的角色列表，可为空") List<String> characters,
            @P("当前集数/时间点，如 第6集 12分30秒，可为空") String currentEpisode,
            @P("当前检索场景描述，可为空") String searchScene,
            @P("已确认的视觉特征摘要，可为空") String visualFeatures
    ) {
        String sessionId = requireSessionId();
        blackboardService.lockContext(
                sessionId, workTitle, characters, currentEpisode, searchScene, visualFeatures
        );
        return "已锁定会话上下文：" + blackboardService.buildContextSummary(sessionId);
    }

    @Tool("读取当前会话已锁定的实体黑板状态。")
    public String getSessionContext() {
        String sessionId = requireSessionId();
        return blackboardService.buildContextSummary(sessionId);
    }

    @Tool("清除当前会话的实体锚点。仅当用户明确换话题或换作品时调用。")
    public String clearSessionContext() {
        String sessionId = requireSessionId();
        blackboardService.clear(sessionId);
        return "已清除会话实体锚点，可开始新的检索领域。";
    }

    private static String requireSessionId() {
        String sessionId = SessionContextHolder.get();
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("会话 ID 未绑定，无法操作黑板");
        }
        return sessionId;
    }
}
