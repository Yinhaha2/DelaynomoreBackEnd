package com.agentcrawler.streaming;

import com.agentcrawler.core.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class StreamEmitter {
    private final int maxChunkChars;
    private final Consumer<String> frameConsumer;
    private final Object writeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
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
            emit(SseEncoder.textDelta(piece));
        }
    }

    public void thinking(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        for (String piece : splitUtf8Safe(content.strip() + "\n", maxChunkChars)) {
            emit(SseEncoder.thinkingDelta(piece));
        }
    }

    public void link(String url, String title, String description) {
        emit(SseEncoder.link(url, title, description));
    }

    public void image(String url, String alt) {
        emit(SseEncoder.image(url, alt));
    }

    public void video(String url, String title, String format, String sourcePage, String roadName) {
        emit(SseEncoder.video(url, title, format, sourcePage, roadName));
    }

    public void resourceBundle(Map<String, Object> payload) {
        emit(SseEncoder.resourceBundle(payload));
    }

    public void done(String messageId, String conversationId) {
        done(messageId, conversationId, null);
    }

    public void done(String messageId, String conversationId, String title) {
        emit(SseEncoder.done(messageId, conversationId, title));
    }

    public void error(ErrorCode code, String message) {
        emit(SseEncoder.error(code.name(), message));
    }

    public String collectedText() {
        return String.join("", textParts);
    }

    public void close() {
        closed.set(true);
    }

    private void emit(String frame) {
        if (closed.get()) {
            return;
        }
        synchronized (writeLock) {
            if (closed.get()) {
                return;
            }
            frameConsumer.accept(frame);
        }
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
