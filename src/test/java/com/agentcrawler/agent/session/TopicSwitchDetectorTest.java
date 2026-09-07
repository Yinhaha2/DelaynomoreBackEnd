package com.agentcrawler.agent.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicSwitchDetectorTest {

    @Test
    void detectsExplicitTopicSwitch() {
        assertThat(TopicSwitchDetector.isTopicSwitch("我们换一部动漫吧")).isTrue();
        assertThat(TopicSwitchDetector.isTopicSwitch("换个话题")).isTrue();
    }

    @Test
    void ignoresNormalFollowUp() {
        assertThat(TopicSwitchDetector.isTopicSwitch("这一集他们最后打赢了吗")).isFalse();
    }
}
