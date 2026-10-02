package com.agentcrawler.streaming;

/**
 * 用户终止，或同一站点进度连续空转达到上限时抛出。调用方不要吞掉。
 */
public final class GenerationStoppedException extends RuntimeException {
    private final GenerationRuns.StopReason reason;

    public GenerationStoppedException(GenerationRuns.StopReason reason) {
        super(message(reason));
        this.reason = reason == null ? GenerationRuns.StopReason.USER : reason;
    }

    public GenerationRuns.StopReason reason() {
        return reason;
    }

    public static void rethrow(Throwable error) {
        if (error instanceof GenerationStoppedException stopped) {
            throw stopped;
        }
    }

    private static String message(GenerationRuns.StopReason reason) {
        if (reason == GenerationRuns.StopReason.SITE_RETRY_LIMIT) {
            return "当前站点已重试 5 次，停止检索。";
        }
        return "已停止生成";
    }
}
