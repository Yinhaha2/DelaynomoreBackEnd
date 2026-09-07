package com.agentcrawler.agent.langchain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTokenGateTest {

    @Test
    void discardsNarrationBeforeToolsAndKeepsFinalAnswer() {
        AgentTokenGate gate = new AgentTokenGate();
        gate.append("好的，我来帮你检索。");
        gate.append("首先让我确认上下文。");
        gate.discardIntermediate();
        gate.append("检索站点暂时无法访问，请稍后再试。");

        assertEquals("检索站点暂时无法访问，请稍后再试。", gate.takeFinalText());
        assertEquals("", gate.takeFinalText());
    }

    @Test
    void keepsDirectReplyWhenNoTools() {
        AgentTokenGate gate = new AgentTokenGate();
        gate.append("你好，我是检索助手。");
        assertEquals("你好，我是检索助手。", gate.takeFinalText());
    }
}
