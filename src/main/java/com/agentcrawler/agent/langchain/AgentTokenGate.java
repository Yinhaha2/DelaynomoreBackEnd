package com.agentcrawler.agent.langchain;

/**
 * 工具调用前的 token 是过程旁白（「我来检索」），不能当成给用户的最终回复。
 * 每次工具执行完丢掉这段旁白；流结束时剩下的才是推荐语。旁白改走思考流。
 */
public final class AgentTokenGate {
    private final StringBuilder pending = new StringBuilder();

    public synchronized void append(String token) {
        if (token != null && !token.isEmpty()) {
            pending.append(token);
        }
    }

    public synchronized String discardIntermediate() {
        String text = pending.toString();
        pending.setLength(0);
        return text;
    }

    public synchronized String takeFinalText() {
        String text = pending.toString();
        pending.setLength(0);
        return text;
    }
}
