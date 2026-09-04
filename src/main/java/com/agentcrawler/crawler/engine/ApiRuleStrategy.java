package com.agentcrawler.crawler.engine;

import com.agentcrawler.crawler.model.ApiChapterConfig;
import com.agentcrawler.crawler.model.ApiEpisodePageConfig;
import com.agentcrawler.crawler.model.ApiRequestConfig;
import com.agentcrawler.crawler.model.ApiSearchConfig;
import com.agentcrawler.crawler.model.PreparedRuleRequest;
import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import com.agentcrawler.crawler.util.TemplateRenderer;
import com.agentcrawler.crawler.util.UrlNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiRuleStrategy {
    private static final Pattern EXACT_VARIABLE = Pattern.compile("^@([A-Za-z_][A-Za-z0-9_]*)$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PreparedRuleRequest prepareRequest(ApiRequestConfig config, Map<String, Object> variables) {
        String method = config.getMethod() == null ? "GET" : config.getMethod().toUpperCase();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new ApiRuleFormatException("仅支持 GET/POST，当前为 " + method);
        }
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new ApiRuleFormatException("API 请求 URL 不能为空");
        }
        String url = renderTemplate(config.getUrl().trim(), variables, true);
        URI uri = URI.create(url);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ApiRuleFormatException("API 请求 URL 无效: " + url);
        }
        boolean hasBody = "POST".equals(method) && !"none".equalsIgnoreCase(config.getBodyType());
        return new PreparedRuleRequest(
                method,
                url,
                renderStringMap(config.getHeaders(), variables),
                renderStringMap(config.getQuery(), variables),
                config.getBodyType(),
                hasBody ? renderValue(config.getBody(), variables) : null
        );
    }

    public List<SearchItem> parseSearch(String raw, ApiSearchConfig config, String baseUrl) {
        validateSearchConfig(config);
        Object document = decodeResponse(raw);
        List<Object> nodes = RestrictedJsonPath.read(document, config.getListPath());
        List<SearchItem> items = new ArrayList<>();
        for (Object node : nodes) {
            String name = stringValue(RestrictedJsonPath.readFirst(node, config.getNamePath()));
            String source = stringValue(RestrictedJsonPath.readFirst(node, config.getSourcePath()));
            if (name.isBlank() || source.isBlank()) {
                continue;
            }
            String imageUrl = "";
            if (config.getImagePath() != null && !config.getImagePath().isBlank()) {
                imageUrl = stringValue(RestrictedJsonPath.readFirst(node, config.getImagePath()));
                if (!imageUrl.isBlank()) {
                    imageUrl = UrlNormalizer.normalizeEpisodeUrl(baseUrl, imageUrl);
                }
            }
            items.add(new SearchItem(
                    name,
                    UrlNormalizer.normalizeEpisodeUrl(baseUrl, source),
                    imageUrl
            ));
        }
        return items;
    }

    public List<Road> parseChapters(String raw, ApiChapterConfig config, String source, String baseUrl) {
        validateChapterConfig(config);
        Object document = decodeResponse(raw);
        Map<String, Object> rootVariables = new LinkedHashMap<>();
        rootVariables.put("source", source);
        for (Map.Entry<String, String> entry : config.getVariables().entrySet()) {
            Object value = RestrictedJsonPath.readFirst(document, entry.getValue());
            if (value == null) {
                throw new ApiRuleFormatException(
                        "章节响应变量 " + entry.getKey() + " 未匹配到值: " + entry.getValue()
                );
            }
            rootVariables.put(entry.getKey(), value);
        }
        return config.isDelimited()
                ? parseDelimited(document, config, rootVariables, baseUrl)
                : parseNested(document, config, rootVariables, baseUrl);
    }

    public void validateSearchConfig(ApiSearchConfig config) {
        RestrictedJsonPath.validate(config.getListPath());
        RestrictedJsonPath.validate(config.getNamePath());
        RestrictedJsonPath.validate(config.getSourcePath());
        if (config.getImagePath() != null && !config.getImagePath().isBlank()) {
            RestrictedJsonPath.validate(config.getImagePath());
        }
    }

    public void validateChapterConfig(ApiChapterConfig config) {
        for (String path : config.getVariables().values()) {
            RestrictedJsonPath.validate(path);
        }
        if (config.isDelimited()) {
            RestrictedJsonPath.validate(config.getRoadNamesPath());
            RestrictedJsonPath.validate(config.getRoadEpisodesPath());
            if (config.getRoadSeparator().isEmpty()
                    || config.getEpisodeSeparator().isEmpty()
                    || config.getFieldSeparator().isEmpty()) {
                throw new ApiRuleFormatException("章节分隔符不能为空");
            }
            return;
        }
        if (config.getRoadsPath() != null && !config.getRoadsPath().isBlank()) {
            RestrictedJsonPath.validate(config.getRoadsPath());
        }
        if (config.getRoadNamePath() != null && !config.getRoadNamePath().isBlank()) {
            RestrictedJsonPath.validate(config.getRoadNamePath());
        }
        RestrictedJsonPath.validate(config.getEpisodesPath());
        RestrictedJsonPath.validate(config.getEpisodeNamePath());
        if (config.getEpisodeUrlPath() != null && !config.getEpisodeUrlPath().isBlank()) {
            RestrictedJsonPath.validate(config.getEpisodeUrlPath());
        } else if (config.getEpisodePage() == null) {
            throw new ApiRuleFormatException("必须配置播放入口地址路径或播放页地址模板");
        }
        if (config.getEpisodePage() != null && config.getEpisodePage().getUrl().isBlank()) {
            throw new ApiRuleFormatException("播放页地址模板不能为空");
        }
    }

    private List<Road> parseNested(
            Object document,
            ApiChapterConfig config,
            Map<String, Object> rootVariables,
            String baseUrl
    ) {
        boolean hasRoads = config.getRoadsPath() != null && !config.getRoadsPath().isBlank();
        List<Object> roadNodes = hasRoads
                ? RestrictedJsonPath.read(document, config.getRoadsPath())
                : List.of(document);
        List<Road> roads = new ArrayList<>();
        for (int roadIndex = 0; roadIndex < roadNodes.size(); roadIndex++) {
            Object roadNode = roadNodes.get(roadIndex);
            String roadName = hasRoads && config.getRoadNamePath() != null && !config.getRoadNamePath().isBlank()
                    ? stringValue(RestrictedJsonPath.readFirst(roadNode, config.getRoadNamePath()))
                    : "";
            List<Object> episodeNodes = RestrictedJsonPath.read(roadNode, config.getEpisodesPath());
            List<String> names = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            for (int episodeIndex = 0; episodeIndex < episodeNodes.size(); episodeIndex++) {
                Object episodeNode = episodeNodes.get(episodeIndex);
                String episodeName = stringValue(
                        RestrictedJsonPath.readFirst(episodeNode, config.getEpisodeNamePath())
                );
                String rawUrl = config.getEpisodeUrlPath() == null || config.getEpisodeUrlPath().isBlank()
                        ? ""
                        : stringValue(RestrictedJsonPath.readFirst(episodeNode, config.getEpisodeUrlPath()));
                String pageUrl = resolveEpisodeUrl(
                        config,
                        rootVariables,
                        rawUrl,
                        roadIndex,
                        episodeIndex,
                        baseUrl
                );
                if (pageUrl.isBlank()) {
                    continue;
                }
                urls.add(pageUrl);
                names.add(episodeName.isBlank() ? "第" + (episodeIndex + 1) + "集" : episodeName);
            }
            if (urls.isEmpty()) {
                continue;
            }
            roads.add(new Road(
                    roadName.isBlank() ? "播放线路" + (roads.size() + 1) : roadName,
                    names,
                    urls
            ));
        }
        return roads;
    }

    private List<Road> parseDelimited(
            Object document,
            ApiChapterConfig config,
            Map<String, Object> rootVariables,
            String baseUrl
    ) {
        String namesValue = stringValue(RestrictedJsonPath.readFirst(document, config.getRoadNamesPath()));
        String episodesValue = stringValue(RestrictedJsonPath.readFirst(document, config.getRoadEpisodesPath()));
        if (episodesValue.isBlank()) {
            return List.of();
        }
        String[] roadNames = namesValue.split(Pattern.quote(config.getRoadSeparator()));
        String[] roadGroups = episodesValue.split(Pattern.quote(config.getRoadSeparator()));
        List<Road> roads = new ArrayList<>();
        for (int roadIndex = 0; roadIndex < roadGroups.length; roadIndex++) {
            String[] entries = roadGroups[roadIndex].split(Pattern.quote(config.getEpisodeSeparator()));
            List<String> names = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            for (int episodeIndex = 0; episodeIndex < entries.length; episodeIndex++) {
                String entry = entries[episodeIndex].trim();
                if (entry.isEmpty()) {
                    continue;
                }
                int separatorIndex = entry.indexOf(config.getFieldSeparator());
                if (separatorIndex < 0) {
                    continue;
                }
                String name = entry.substring(0, separatorIndex).trim();
                String rawUrl = entry.substring(separatorIndex + config.getFieldSeparator().length()).trim();
                String pageUrl = resolveEpisodeUrl(
                        config,
                        rootVariables,
                        rawUrl,
                        roadIndex,
                        episodeIndex,
                        baseUrl
                );
                if (pageUrl.isBlank()) {
                    continue;
                }
                urls.add(pageUrl);
                names.add(name.isBlank() ? "第" + (episodeIndex + 1) + "集" : name);
            }
            if (urls.isEmpty()) {
                continue;
            }
            String configuredName = roadIndex < roadNames.length ? roadNames[roadIndex].trim() : "";
            roads.add(new Road(
                    configuredName.isBlank() ? "播放线路" + (roads.size() + 1) : configuredName,
                    names,
                    urls
            ));
        }
        return roads;
    }

    private String resolveEpisodeUrl(
            ApiChapterConfig config,
            Map<String, Object> rootVariables,
            String rawUrl,
            int roadIndex,
            int episodeIndex,
            String baseUrl
    ) {
        ApiEpisodePageConfig page = config.getEpisodePage();
        if (page == null) {
            return UrlNormalizer.normalizeEpisodeUrl(baseUrl, rawUrl);
        }
        if (page.getUrl().isBlank()) {
            throw new ApiRuleFormatException("播放页地址模板不能为空");
        }
        Map<String, Object> variables = new LinkedHashMap<>(rootVariables);
        variables.put("episodeUrl", rawUrl);
        variables.put("roadIndex", roadIndex);
        variables.put("roadNumber", roadIndex + 1);
        variables.put("episodeIndex", episodeIndex);
        variables.put("episodeNumber", episodeIndex + 1);
        String path = renderTemplate(page.getUrl(), variables, true);
        URI uri = URI.create(path);
        Map<String, String> mergedQuery = new LinkedHashMap<>(parseQuery(uri.getRawQuery()));
        mergedQuery.putAll(renderStringMap(page.getQuery(), variables));
        URI rebuilt = URI.create(buildUrl(uri, mergedQuery));
        return UrlNormalizer.normalizeEpisodeUrl(baseUrl, rebuilt.toString());
    }

    private Object decodeResponse(String raw) {
        try {
            return MAPPER.readValue(raw, Object.class);
        } catch (Exception ex) {
            throw new ApiRuleFormatException("API 响应不是有效 JSON: " + ex.getMessage());
        }
    }

    private Map<String, String> renderStringMap(Map<String, Object> input, Map<String, Object> variables) {
        Map<String, String> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            rendered.put(
                    renderTemplate(entry.getKey(), variables, false),
                    stringValue(renderValue(entry.getValue(), variables))
            );
        }
        return rendered;
    }

    private Object renderValue(Object value, Map<String, Object> variables) {
        if (value instanceof String text) {
            Matcher exact = EXACT_VARIABLE.matcher(text);
            if (exact.matches()) {
                String name = exact.group(1);
                if (!variables.containsKey(name)) {
                    throw new ApiRuleFormatException("缺少模板变量 @" + name);
                }
                return variables.get(name);
            }
            return renderTemplate(text, variables, false);
        }
        if (value instanceof List<?> list) {
            List<Object> rendered = new ArrayList<>();
            for (Object item : list) {
                rendered.add(renderValue(item, variables));
            }
            return rendered;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                rendered.put(String.valueOf(entry.getKey()), renderValue(entry.getValue(), variables));
            }
            return rendered;
        }
        return value;
    }

    private String renderTemplate(String template, Map<String, Object> variables, boolean encode) {
        Map<String, String> stringVariables = new LinkedHashMap<>();
        variables.forEach((key, val) -> stringVariables.put(key, val == null ? "" : String.valueOf(val)));
        return encode
                ? TemplateRenderer.renderEncoded(template, stringVariables)
                : TemplateRenderer.render(template, stringVariables);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text.trim();
        }
        return String.valueOf(value).trim();
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                query.put(parts[0], parts[1]);
            }
        }
        return query;
    }

    private String buildUrl(URI uri, Map<String, String> query) {
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://").append(uri.getAuthority());
        if (uri.getPath() != null) {
            builder.append(uri.getPath());
        }
        if (!query.isEmpty()) {
            builder.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) {
                    builder.append('&');
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        return builder.toString();
    }
}
