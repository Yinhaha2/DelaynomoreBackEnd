package com.agentcrawler.agent.session;

import java.util.regex.Pattern;

public final class TopicSwitchDetector {
    private static final Pattern SWITCH_PATTERN = Pattern.compile(
            "(换(一|个)?部|换个话题|不是这个|另一部|重新找|别的话题|不看这个|换一个动漫|换个番|停止当前|取消锁定)",
            Pattern.CASE_INSENSITIVE
    );

    private TopicSwitchDetector() {}

    public static boolean isTopicSwitch(String message) {
        return message != null && SWITCH_PATTERN.matcher(message).find();
    }
}
