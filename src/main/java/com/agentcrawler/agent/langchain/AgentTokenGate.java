package com.agentcrawler.agent.langchain;

/**
 * 丢掉模型在工具调用前的过程旁白，只保留最后一轮对用户可见的回复。
 */
public final class AgentTokenGate {
    private final StringBuilder pending = new StringBuilder();

    public synchronized void append(String token) {
        if (token != null && !token.isEmpty()) {
            pending.append(token);
        }
    }

    public synchronized void discardIntermediate() {
        pending.setLength(0);
    }

    public synchronized String takeFinalText() {
        String text = pending.toString();
        pending.setLength(0);
        return text;
    }
}
