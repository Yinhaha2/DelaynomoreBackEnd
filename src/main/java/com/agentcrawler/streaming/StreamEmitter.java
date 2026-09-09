package com.agentcrawler.streaming;

import com.agentcrawler.core.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class StreamEmitter {
    private final int maxChunkChars;
    private final Consumer<String> frameConsumer;
    private final List<String> textParts = new ArrayList<>();

    public StreamEmitter(int maxChunkChars, Consumer<String> frameConsumer) {
        this.maxChunkChars = maxChunkChars;
        this.frameConsumer = frameConsumer;
    }

    public void text(String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        textParts.add(content);
        for (String piece : splitUtf8Safe(content, maxChunkChars)) {
            frameConsumer.accept(SseEncoder.textDelta(piece));
        }
    }

    public void link(String url, String title, String description) {
        frameConsumer.accept(SseEncoder.link(url, title, description));
    }

    public void image(String url, String alt) {
        frameConsumer.accept(SseEncoder.image(url, alt));
    }

    public void video(String url, String title, String format, String sourcePage, String roadName) {
        frameConsumer.accept(SseEncoder.video(url, title, format, sourcePage, roadName));
    }

    public void resourceBundle(Map<String, Object> payload) {
        frameConsumer.accept(SseEncoder.resourceBundle(payload));
    }

    public void done(String messageId, String conversationId) {
        done(messageId, conversationId, null);
    }

    public void done(String messageId, String conversationId, String title) {
        frameConsumer.accept(SseEncoder.done(messageId, conversationId, title));
    }

    public void error(ErrorCode code, String message) {
        frameConsumer.accept(SseEncoder.error(code.name(), message));
    }

    public String collectedText() {
        return String.join("", textParts);
    }

    static List<String> splitUtf8Safe(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                while (end > start && Character.isLowSurrogate(text.charAt(end - 1))) {
                    end--;
                }
                if (end == start) {
                    end = Math.min(start + maxChars, text.length());
                }
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }
}
