package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.model.CrawlResourceResult.VideoResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把爬虫扁平 {@link VideoResource} 列表按播放线路（CDN / 插件 road）归纳，供 SSE 卡片与 LLM 摘要使用。
 */
public final class ResourceRouteGrouper {
    private static final Pattern EPISODE_NUMBER = Pattern.compile(
            "(?:第\\s*0*(\\d+)\\s*[集话回]|[Ee][Pp]\\.?\\s*0*(\\d+)|Episode\\s*0*(\\d+))"
    );

    private ResourceRouteGrouper() {}

    public record GroupedResources(
            String title,
            List<Route> routes,
            int uniqueEpisodeCount,
            String latestEpisode
    ) {}

    public record Route(
            String name,
            String kind,
            boolean recommended,
            List<Episode> episodes
    ) {}

    public record Episode(
            String title,
            String url,
            String format,
            String sourcePage,
            String roadName
    ) {}

    public static GroupedResources group(CrawlResourceResult result) {
        String title = result == null || result.keyword() == null ? "" : result.keyword().trim();
        List<VideoResource> videos = result == null || result.videos() == null ? List.of() : result.videos();
        if (videos.isEmpty()) {
            return new GroupedResources(title, List.of(), 0, null);
        }

        Map<String, RouteAccumulator> buckets = new LinkedHashMap<>();
        boolean groupByRoad = distinctRoadNames(videos).size() >= 2;
        int index = 0;
        for (VideoResource video : videos) {
            if (video == null || video.url() == null || video.url().isBlank()) {
                continue;
            }
            String familyId = familyId(video.url());
            String key = groupByRoad ? roadKey(video) : familyId;
            RouteAccumulator acc = buckets.computeIfAbsent(key, ignored -> new RouteAccumulator(
                    groupByRoad ? displayRoadName(video) : displayFamilyName(familyId, video.url()),
                    familyId
            ));
            acc.add(video, index++);
        }

        List<Route> routes = new ArrayList<>(buckets.values().stream()
                .map(RouteAccumulator::toRoute)
                .sorted(routeOrder())
                .toList());
        if (!routes.isEmpty() && routes.stream().noneMatch(Route::recommended)) {
            Route first = routes.get(0);
            routes.set(0, new Route(first.name(), first.kind(), true, first.episodes()));
        }

        Set<String> uniqueTitles = new LinkedHashSet<>();
        for (Route route : routes) {
            for (Episode episode : route.episodes()) {
                uniqueTitles.add(episode.title());
            }
        }
        return new GroupedResources(title, routes, uniqueTitles.size(), pickLatestEpisode(uniqueTitles));
    }

    public static String detectFormat(String url) {
        if (url == null) {
            return "unknown";
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".m3u8") || lower.contains("m3u8.php")) {
            return "m3u8";
        }
        if (lower.contains(".mp4")) {
            return "mp4";
        }
        return "unknown";
    }

    static String familyId(String url) {
        String host = hostOf(url);
        String lower = url.toLowerCase(Locale.ROOT);
        if (host.contains("cloudflarestorage")
                || host.contains("r2.dev")
                || host.contains(".r2.")
                || host.startsWith("silivideo.")) {
            return "r2";
        }
        if (host.contains("silisililove") || lower.contains("m3u8.php")) {
            return "sili";
        }
        if (host.contains("ffzy")) {
            return "ffzy";
        }
        if (host.contains("yhdm") || host.contains("iyinghua")) {
            return "yhdm";
        }
        return host.isBlank() ? "other" : "host:" + host;
    }

    static String displayFamilyName(String familyId, String url) {
        return switch (familyId) {
            case "r2" -> "高速直链 (R2)";
            case "sili" -> "Sili 专属源";
            case "ffzy" -> "非凡云播 (m3u8)";
            case "yhdm" -> "樱花动漫";
            case "other" -> "其他线路";
            default -> {
                String host = hostOf(url);
                yield host.isBlank() ? "其他线路" : host;
            }
        };
    }

    static String kindOf(String familyId, String format) {
        if ("r2".equals(familyId) || "mp4".equals(format)) {
            return "mp4";
        }
        if ("m3u8".equals(format) || "sili".equals(familyId) || "ffzy".equals(familyId) || "yhdm".equals(familyId)) {
            return "hls";
        }
        return "unknown";
    }

    static int episodeNumber(String title) {
        if (title == null) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = EPISODE_NUMBER.matcher(title);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            if (group != null) {
                return Integer.parseInt(group);
            }
        }
        return Integer.MAX_VALUE;
    }

    private static Set<String> distinctRoadNames(List<VideoResource> videos) {
        Set<String> names = new LinkedHashSet<>();
        for (VideoResource video : videos) {
            if (video == null) {
                continue;
            }
            String road = video.roadName() == null ? "" : video.roadName().trim();
            if (!road.isEmpty()) {
                names.add(road);
            }
        }
        return names;
    }

    private static String roadKey(VideoResource video) {
        String road = video.roadName() == null ? "" : video.roadName().trim();
        return road.isEmpty() ? "road:default" : "road:" + road;
    }

    private static String displayRoadName(VideoResource video) {
        String road = video.roadName() == null ? "" : video.roadName().trim();
        return road.isEmpty() ? displayFamilyName(familyId(video.url()), video.url()) : road;
    }

    private static String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return "";
        }
    }

    private static Comparator<Route> routeOrder() {
        return Comparator
                .comparing((Route route) -> !route.recommended())
                .thenComparing(route -> switch (route.kind()) {
                    case "mp4" -> 0;
                    case "hls" -> 1;
                    default -> 2;
                });
    }

    private static String pickLatestEpisode(Set<String> titles) {
        String latest = null;
        int best = Integer.MIN_VALUE;
        for (String title : titles) {
            int number = episodeNumber(title);
            if (number != Integer.MAX_VALUE && number >= best) {
                best = number;
                latest = title;
            }
        }
        if (latest != null) {
            return latest;
        }
        return titles.isEmpty() ? null : titles.stream().reduce((a, b) -> b).orElse(null);
    }

    private static final class RouteAccumulator {
        private final String name;
        private final String familyId;
        private final Map<String, IndexedEpisode> episodes = new LinkedHashMap<>();

        private RouteAccumulator(String name, String familyId) {
            this.name = name;
            this.familyId = familyId;
        }

        private void add(VideoResource video, int index) {
            String title = video.title() == null || video.title().isBlank() ? "未命名" : video.title().trim();
            episodes.putIfAbsent(title, new IndexedEpisode(
                    new Episode(
                            title,
                            video.url(),
                            detectFormat(video.url()),
                            video.sourcePage(),
                            video.roadName()
                    ),
                    index
            ));
        }

        private Route toRoute() {
            List<Episode> sorted = episodes.values().stream()
                    .sorted(Comparator
                            .comparingInt((IndexedEpisode item) -> episodeNumber(item.episode.title()))
                            .thenComparingInt(item -> item.index))
                    .map(item -> item.episode)
                    .toList();
            String format = sorted.isEmpty() ? "unknown" : sorted.get(0).format();
            String kind = kindOf(familyId, format);
            boolean recommended = "r2".equals(familyId) || "mp4".equals(kind);
            return new Route(name, kind, recommended, List.copyOf(sorted));
        }
    }

    private record IndexedEpisode(Episode episode, int index) {}
}
