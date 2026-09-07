package com.agentcrawler.service;

import com.agentcrawler.agent.session.EntityExtractor;

import java.util.regex.Pattern;

/**
 * 为首轮对话生成短标题（12～24 字为宜），供前端历史列表展示。
 */
public final class ConversationTitleGenerator {

    static final int MAX_CHARS = 24;
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://\\S+|magnet:\\?\\S+|www\\.\\S+)");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s　]+");
    private static final Pattern PUNCT_EDGE = Pattern.compile("^[\\p{Punct}，。！？、；：]+|[\\p{Punct}，。！？、；：]+$");

    private ConversationTitleGenerator() {
    }

    public static String generate(String userMessage, boolean hasAttachments, String lockedWorkTitle) {
        if (lockedWorkTitle != null && !lockedWorkTitle.isBlank()) {
            return clip(lockedWorkTitle.trim() + intentSuffix(userMessage));
        }
        String quoted = EntityExtractor.extractWorkTitle(userMessage).orElse("");
        if (!quoted.isBlank()) {
            return clip(quoted + intentSuffix(userMessage));
        }
        if (hasAttachments && isImageOnlyPrompt(userMessage)) {
            return "图片识图";
        }
        String compact = compact(userMessage);
        if (compact.isBlank()) {
            return hasAttachments ? "图片识图" : null;
        }
        return clip(compact);
    }

    private static boolean isImageOnlyPrompt(String userMessage) {
        return userMessage == null
                || userMessage.isBlank()
                || "请分析这张图片并告诉我出处。".equals(userMessage.trim());
    }

    static String intentSuffix(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.contains("哪一集") || message.contains("出处") || message.contains("表情包")
                || message.contains("这张图") || message.contains("识别")) {
            return "出处";
        }
        if (message.contains("播放") || message.contains("资源") || message.contains("在线看")
                || message.contains("能看") || message.contains("找一部")) {
            return "播放资源";
        }
        return "";
    }

    static String compact(String message) {
        if (message == null) {
            return "";
        }
        String withoutUrls = URL.matcher(message).replaceAll(" ");
        String collapsed = WHITESPACE.matcher(withoutUrls).replaceAll("");
        return PUNCT_EDGE.matcher(collapsed).replaceAll("").trim();
    }

    static String clip(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) <= MAX_CHARS) {
            return trimmed;
        }
        int end = trimmed.offsetByCodePoints(0, MAX_CHARS);
        return trimmed.substring(0, end);
    }
}
