package com.agentcrawler.link;

import com.agentcrawler.agent.session.EntityExtractor;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorkTitleNormalizer {

    private static final Pattern EPISODE = Pattern.compile("第\\s*(\\d+)\\s*[集话話回]");
    private static final Set<String> JUNK_TITLES = Set.of(
            "just a moment", "attention required", "access denied", "404",
            "not found", "403 forbidden", "please wait", "验证", "安全验证"
    );
    private static final String[] SITE_SUFFIXES = {
            " - 哔哩哔哩", " - bilibili", " - YouTube", " | YouTube",
            " | Bangumi 番组计划", " - Bangumi", " - 豆瓣", " | 豆瓣",
            " - 维基百科", " - Wikipedia"
    };

    private WorkTitleNormalizer() {
    }

    public static NormalizedTitle normalize(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank() || isJunk(rawTitle)) {
            return NormalizedTitle.empty();
        }
        String cleaned = stripSiteSuffix(rawTitle.trim());
        List<String> quoted = EntityExtractor.extractQuotedTitles(cleaned);
        String work = quoted.isEmpty() ? stripEpisodeSuffix(cleaned) : quoted.get(0);
        String episode = EntityExtractor.extractEpisodeHint(cleaned);
        if (episode.isBlank()) {
            Matcher matcher = EPISODE.matcher(cleaned);
            if (matcher.find()) {
                episode = "第" + matcher.group(1) + "集";
            }
        }
        return new NormalizedTitle(work.trim(), episode, quoted.isEmpty() ? 0.72 : 0.9);
    }

    static boolean isJunk(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return JUNK_TITLES.stream().anyMatch(lower::contains);
    }

    private static String stripSiteSuffix(String title) {
        String current = title;
        for (String suffix : SITE_SUFFIXES) {
            if (current.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                current = current.substring(0, current.length() - suffix.length()).trim();
            }
        }
        int pipe = current.lastIndexOf('|');
        if (pipe > 3) {
            current = current.substring(0, pipe).trim();
        }
        return current;
    }

    private static String stripEpisodeSuffix(String title) {
        Matcher matcher = EPISODE.matcher(title);
        if (matcher.find() && matcher.start() > 1) {
            return title.substring(0, matcher.start()).replaceAll("[-_：:\\s]+$", "").trim();
        }
        return title;
    }

    public record NormalizedTitle(String workTitle, String episodeHint, double confidence) {
        static NormalizedTitle empty() {
            return new NormalizedTitle("", "", 0);
        }

        public boolean hasWorkTitle() {
            return workTitle != null && !workTitle.isBlank();
        }
    }
}
