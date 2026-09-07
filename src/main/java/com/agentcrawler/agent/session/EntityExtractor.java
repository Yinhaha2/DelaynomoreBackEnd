package com.agentcrawler.agent.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EntityExtractor {
    private static final Pattern QUOTED_TITLE = Pattern.compile("《([^》]+)》");
    private static final Pattern EPISODE = Pattern.compile("第\\s*(\\d+)\\s*集");
    private static final Pattern TIMESTAMP = Pattern.compile("(\\d{1,2})\\s*分\\s*(\\d{1,2})\\s*秒");
    private static final Pattern CHARACTER_SPLIT = Pattern.compile("[、,，/\\s]+");

    private EntityExtractor() {}

    public static List<String> extractQuotedTitles(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        Matcher matcher = QUOTED_TITLE.matcher(message);
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            titles.add(matcher.group(1).trim());
        }
        return titles;
    }

    public static String extractEpisodeHint(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        Matcher episodeMatcher = EPISODE.matcher(message);
        if (episodeMatcher.find()) {
            String base = "第" + episodeMatcher.group(1) + "集";
            Matcher timeMatcher = TIMESTAMP.matcher(message);
            if (timeMatcher.find()) {
                return base + " " + timeMatcher.group(1) + "分" + timeMatcher.group(2) + "秒";
            }
            return base;
        }
        return "";
    }

    public static List<String> parseCharacterList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> characters = new ArrayList<>();
        for (String part : CHARACTER_SPLIT.split(raw.trim())) {
            if (!part.isBlank()) {
                characters.add(part.trim());
            }
        }
        return characters;
    }

    public static Optional<String> extractWorkTitle(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        List<String> quoted = extractQuotedTitles(message);
        if (!quoted.isEmpty()) {
            return Optional.of(quoted.get(0));
        }
        return Optional.empty();
    }

    public static List<String> extractCharacters(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<String> characters = new ArrayList<>();
        Pattern rolePattern = Pattern.compile("(?:角色|人物)[是为：:\\s]+([\\u4e00-\\u9fa5A-Za-z0-9·\\s、,，/]+)");
        Matcher matcher = rolePattern.matcher(message);
        while (matcher.find()) {
            characters.addAll(parseCharacterList(matcher.group(1)));
        }
        return characters;
    }

    public static String primaryWorkKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        List<String> quoted = extractQuotedTitles(keyword);
        if (!quoted.isEmpty()) {
            return quoted.get(0);
        }
        return keyword.trim();
    }
}
