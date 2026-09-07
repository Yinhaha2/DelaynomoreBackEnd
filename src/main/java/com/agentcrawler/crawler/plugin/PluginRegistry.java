package com.agentcrawler.crawler.plugin;

import com.agentcrawler.crawler.model.PluginRule;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PluginRegistry {
    private final ObjectMapper objectMapper;
    private final Map<String, PluginRule> pluginsByName = new LinkedHashMap<>();
    private final Map<String, PluginRule> pluginsByHost = new LinkedHashMap<>();

    public PluginRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadPlugins() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:plugins/*.json");
        for (Resource resource : resources) {
            try (InputStream inputStream = resource.getInputStream()) {
                PluginRule rule = objectMapper.readValue(inputStream, PluginRule.class);
                pluginsByName.put(rule.getName().toLowerCase(Locale.ROOT), rule);
                String host = extractHost(rule.getBaseURL());
                if (!host.isBlank()) {
                    pluginsByHost.put(host, rule);
                }
            }
        }
    }

    public PluginRule resolve(String site) {
        if (site == null || site.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "site 不能为空");
        }
        String normalized = site.trim();
        PluginRule byName = pluginsByName.get(normalized.toLowerCase(Locale.ROOT));
        if (byName != null) {
            return requireUsable(byName, site);
        }
        String host = extractHost(normalized);
        PluginRule byHost = pluginsByHost.get(host);
        if (byHost != null) {
            return requireUsable(byHost, site);
        }
        for (PluginRule rule : pluginsByName.values()) {
            if (rule.getBaseURL().equalsIgnoreCase(normalized)
                    || rule.getBaseURL().equalsIgnoreCase(normalized + "/")) {
                return requireUsable(rule, site);
            }
        }
        throw new AppException(
                ErrorCode.INVALID_REQUEST,
                "未找到站点规则: " + site + "，可用插件: " + String.join(", ", usableNames())
        );
    }

    public Collection<PluginRule> all() {
        return pluginsByName.values();
    }

    public List<PluginRule> usable() {
        return pluginsByName.values().stream()
                .filter(this::isUsable)
                .toList();
    }

    public List<String> usableNames() {
        return usable().stream().map(PluginRule::getName).toList();
    }

    private PluginRule requireUsable(PluginRule rule, String site) {
        if (!isUsable(rule)) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "站点插件不可用: " + site + "，可用插件: " + String.join(", ", usableNames())
            );
        }
        return rule;
    }

    private boolean isUsable(PluginRule rule) {
        return rule.isEnabled() && !rule.isPlaceholder();
    }

    private String extractHost(String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        trimmed = trimmed.replace("https://", "").replace("http://", "");
        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(0, slash);
        }
        return trimmed;
    }
}
