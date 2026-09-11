package com.agentcrawler.crawler.reliability;

import com.agentcrawler.crawler.model.CrawlResourceResult;

import java.util.Locale;

/**
 * Infrastructure failures trip the circuit. Empty catalogs and "title not found" do not.
 */
public final class UpstreamFailureClassifier {
    private UpstreamFailureClassifier() {}

    public static boolean isInfrastructureFailure(Throwable error) {
        if (error == null) {
            return false;
        }
        StringBuilder text = new StringBuilder();
        Throwable cursor = error;
        int depth = 0;
        while (cursor != null && depth++ < 8) {
            if (cursor.getMessage() != null) {
                text.append(' ').append(cursor.getMessage());
            }
            text.append(' ').append(cursor.getClass().getSimpleName());
            cursor = cursor.getCause();
        }
        return isInfrastructureFailureMessage(text.toString());
    }

    public static boolean isInfrastructureFailure(CrawlResourceResult result) {
        if (result == null || result.hasResources()) {
            return false;
        }
        return isInfrastructureFailureMessage(result.error());
    }

    public static boolean isInfrastructureFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "未找到匹配", "未解析到剧集", "没有找到可用资源")) {
            return false;
        }
        return containsAny(
                lower,
                "522", "521", "523", "524",
                "502", "503", "504",
                "timeout", "timed out", "connectexception", "sockettimeoutexception",
                "403", "429",
                "暂时无法访问", "响应超时", "限制访问", "暂时不稳定"
        );
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
