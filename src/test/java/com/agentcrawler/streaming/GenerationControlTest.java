package com.agentcrawler.streaming;

import com.agentcrawler.agent.session.SessionContextHolder;
import okhttp3.Call;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GenerationControlTest {
    @AfterEach
    void tearDown() {
        ThinkingReporter.clear("conv-idle");
        GenerationRuns.end("conv-stop", GenerationRuns.get("conv-stop"));
        SessionContextHolder.clear();
    }

    @Test
    void sameStageStopsBeingReportedAsFreshProgressAfterFiveIdleRepeats() {
        List<String> lines = new ArrayList<>();
        ThinkingReporter.bind("conv-idle", lines::add);
        ThinkingReporter.note("当前站点没有播放地址，改到「嘶哩嘶哩」再试。");

        assertThat(ThinkingReporter.nudgeIdle("conv-idle", 0)).isEqualTo(1);
        assertThat(ThinkingReporter.nudgeIdle("conv-idle", 0)).isEqualTo(2);
        assertThat(ThinkingReporter.nudgeIdle("conv-idle", 0)).isEqualTo(3);
        assertThat(ThinkingReporter.nudgeIdle("conv-idle", 0)).isEqualTo(4);
        assertThat(ThinkingReporter.nudgeIdle("conv-idle", 0)).isEqualTo(5);
        assertThat(lines).filteredOn(line -> line.startsWith("仍在进行：")).hasSize(5);

        ThinkingReporter.note("正在读取下一集。");
        assertThat(ThinkingReporter.nudgeIdle("conv-idle", 0)).isEqualTo(1);
    }

    @Test
    void userStopCancelsTheInFlightSiteCall() {
        SessionContextHolder.set("conv-stop");
        GenerationRuns.GenerationRun run = GenerationRuns.begin("conv-stop");
        Call call = mock(Call.class);
        run.attach(call);

        assertThat(GenerationRuns.stop("conv-stop", GenerationRuns.StopReason.USER)).isTrue();
        verify(call).cancel();
        assertThat(GenerationRuns.stop("conv-stop", GenerationRuns.StopReason.USER)).isFalse();
        assertThatThrownBy(GenerationRuns::checkpoint)
                .isInstanceOf(GenerationStoppedException.class)
                .hasMessage("已停止生成");
    }

    @Test
    void retryLimitStopUsesTheSiteMessage() {
        SessionContextHolder.set("conv-stop");
        GenerationRuns.begin("conv-stop");
        GenerationRuns.stop("conv-stop", GenerationRuns.StopReason.SITE_RETRY_LIMIT);

        assertThatThrownBy(GenerationRuns::checkpoint)
                .isInstanceOf(GenerationStoppedException.class)
                .hasMessage("当前站点已重试 5 次，停止检索。");
    }
}
