package com.agentcrawler.agent.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionBlackboardServiceTest {

    private SessionBlackboardService service;

    @BeforeEach
    void setUp() {
        service = new SessionBlackboardService(new SessionBlackboardStore());
    }

    @Test
    void locksWorkTitleFromQuotedMessage() {
        service.onUserMessage("s1", "帮我找《鬼灭之刃 游郭篇》的资源");

        AnimeSessionState state = service.get("s1");
        assertThat(state.getWorkTitle()).isEqualTo("鬼灭之刃 游郭篇");
        assertThat(state.isLocked()).isTrue();
    }

    @Test
    void injectsAnchorPromptWithStrictScope() {
        service.lockContext(
                "s2",
                "鬼灭之刃 游郭篇",
                java.util.List.of("灶门炭治郎", "堕姬"),
                "第6集 12分30秒",
                "炭治郎开斑纹对决",
                "粉发女鬼"
        );

        String anchor = service.buildAnchorPrompt("s2");

        assertThat(anchor).contains("当前锁定作品：鬼灭之刃 游郭篇");
        assertThat(anchor).contains("灶门炭治郎");
        assertThat(anchor).contains("严格指令");
        assertThat(anchor).contains("防撞脸提示");
    }

    @Test
    void clearsBlackboardOnTopicSwitch() {
        service.lockContext("s3", "鬼灭之刃", null, null, null, null);
        service.onUserMessage("s3", "我们换一部动漫吧");

        assertThat(service.get("s3")).isNull();
    }

    @Test
    void syncsFromSearchResults() {
        service.syncFromSearchResults(
                "s4",
                "《咒术回战》",
                java.util.List.of("咒术回战 第二季 第1集")
        );

        AnimeSessionState state = service.get("s4");
        assertThat(state.getWorkTitle()).isEqualTo("咒术回战");
        assertThat(state.isLocked()).isTrue();
        assertThat(state.getConfidence()).isGreaterThanOrEqualTo(0.8);
    }
}
