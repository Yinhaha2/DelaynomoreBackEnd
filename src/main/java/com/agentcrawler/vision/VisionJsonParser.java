package com.agentcrawler.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VisionJsonParser {

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```");

    private VisionJsonParser() {
    }

    static AnimeVisionAnalysisResult parse(ObjectMapper objectMapper, String raw) {
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            AnimeVisionAnalysisResult result = objectMapper.treeToValue(node, AnimeVisionAnalysisResult.class);
            if (result.getCharacters() == null) {
                result.setCharacters(List.of());
            }
            if (result.getVisualFeatures() == null) {
                result.setVisualFeatures(List.of());
            }
            return result;
        } catch (Exception ex) {
            AnimeVisionAnalysisResult fallback = new AnimeVisionAnalysisResult();
            fallback.setSearchScene(raw == null ? "" : raw.trim());
            fallback.setConfidence(0.0);
            return fallback;
        }
    }

    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = raw.trim();
        Matcher matcher = JSON_BLOCK.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
