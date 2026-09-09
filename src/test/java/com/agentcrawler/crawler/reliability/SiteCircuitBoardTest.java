package com.agentcrawler.crawler.reliability;

import com.agentcrawler.config.CrawlerReliabilityProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class SiteCircuitBoardTest {

    @Test
    void fiveTimeoutsOpenCircuitAndSkipUserTrafficUntilProbe() {
        AtomicLong now = new AtomicLong(1_000_000L);
        SiteCircuitBoard board = new SiteCircuitBoard(CrawlerReliabilityProperties.defaults(), now::get);
        String key = UpstreamKeys.plugin("DM84");

        for (int i = 0; i < 5; i++) {
            board.recordFailure(key);
        }

        assertThat(board.isOpen(key)).isTrue();
        assertThat(board.allowRequest(key)).isFalse();
        assertThat(board.keysAwaitingProbe()).isEmpty();

        now.addAndGet(180_000L);
        assertThat(board.keysAwaitingProbe()).containsExactly(key);
        assertThat(board.allowRequest(key)).isFalse();

        board.recordProbeSuccess(key);
        assertThat(board.isOpen(key)).isFalse();
        assertThat(board.allowRequest(key)).isTrue();
    }

    @Test
    void emptyCatalogDoesNotOpenCircuit() {
        SiteCircuitBoard board = new SiteCircuitBoard(CrawlerReliabilityProperties.defaults());
        String key = UpstreamKeys.fallback("YHDM");
        for (int i = 0; i < 10; i++) {
            if (UpstreamFailureClassifier.isInfrastructureFailure(
                    CrawlResourceResult.failed("x", "YHDM", "YHDM", "樱花动漫未找到匹配结果"))) {
                board.recordFailure(key);
            } else {
                board.recordSuccess(key);
            }
        }
        assertThat(board.isOpen(key)).isFalse();
        assertThat(board.allowRequest(key)).isTrue();
    }

    @Test
    void disabledBoardAlwaysAllows() {
        SiteCircuitBoard board = SiteCircuitBoard.disabled();
        String key = UpstreamKeys.plugin("DM84");
        for (int i = 0; i < 20; i++) {
            board.recordFailure(key);
        }
        assertThat(board.allowRequest(key)).isTrue();
        assertThat(board.keysAwaitingProbe()).isEmpty();
    }
}

class UpstreamFailureClassifierTest {

    @Test
    void treatsTimeoutsAndCloudflareAsInfra() {
        assertThat(UpstreamFailureClassifier.isInfrastructureFailure(new IOException("HTTP 522 for https://x"))).isTrue();
        assertThat(UpstreamFailureClassifier.isInfrastructureFailure(new SocketTimeoutException("timed out"))).isTrue();
        assertThat(UpstreamFailureClassifier.isInfrastructureFailure(
                new AppException(ErrorCode.CRAWL_FAILED, "搜索失败: HTTP 504"))).isTrue();
    }

    @Test
    void ignoresTitleNotFound() {
        assertThat(UpstreamFailureClassifier.isInfrastructureFailure(
                new AppException(ErrorCode.CRAWL_FAILED, "站点 DM84 未找到匹配结果"))).isFalse();
        assertThat(UpstreamFailureClassifier.isInfrastructureFailure(
                CrawlResourceResult.failed("番", "YHDM", "YHDM", "樱花动漫未找到匹配结果"))).isFalse();
        assertThat(UpstreamFailureClassifier.isInfrastructureFailure(
                new CrawlResourceResult("番", "DM84", "DM84", List.of(), List.of(), List.of()))).isFalse();
    }
}
