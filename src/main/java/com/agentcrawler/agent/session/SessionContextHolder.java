package com.agentcrawler.agent.session;

/**
 * 供 Tool 回调获取当前会话 ID（由 LangChainStreamingAgent 在调用 Agent 前设置）。
 */
public final class SessionContextHolder {

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();

    private SessionContextHolder() {
    }

    public static void set(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    public static String get() {
        return SESSION_ID.get();
    }

    public static void clear() {
        SESSION_ID.remove();
    }
}
