package com.agentcrawler.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SseEncoder {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseEncoder() {}

    public static String chunk(Map<String, Object> payload) {
        return encode(SseEventName.CHUNK, payload);
    }

    public static String done(String messageId, String conversationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "done");
        payload.put("message_id", messageId);
        payload.put("conversation_id", conversationId);
        return encode(SseEventName.DONE, payload);
    }

    public static String error(String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", code);
        payload.put("message", message);
        return encode(SseEventName.ERROR, payload);
    }

    public static String textDelta(String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "text_delta");
        payload.put("content", content);
        return chunk(payload);
    }

    public static String link(String url, String title, String description) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "link");
        payload.put("url", url);
        payload.put("title", title);
        if (description != null && !description.isBlank()) {
            payload.put("description", description);
        }
        return chunk(payload);
    }

    public static String image(String url, String alt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "image");
        payload.put("url", url);
        if (alt != null && !alt.isBlank()) {
            payload.put("alt", alt);
        }
        return chunk(payload);
    }

    private static String encode(SseEventName event, Map<String, Object> payload) {
        try {
            return "event: " + event.value() + "\n" + "data: " + MAPPER.writeValueAsString(payload) + "\n\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode SSE payload", e);
        }
    }

    public enum SseEventName {
        CHUNK("chunk"),
        DONE("done"),
        ERROR("error");

        private final String value;

        SseEventName(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
