package com.agentcrawler.crawler.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateRenderer {
    private static final Pattern VARIABLE = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z_][A-Za-z0-9_]*)");

    private TemplateRenderer() {}

    public static String render(String template, Map<String, String> variables) {
        return render(template, variables, false);
    }

    public static String renderEncoded(String template, Map<String, String> variables) {
        return render(template, variables, true);
    }

    private static String render(String template, Map<String, String> variables, boolean encode) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("站点规则缺少 searchURL，无法构造搜索地址");
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.getOrDefault(name, "");
            if (encode) {
                value = URLEncoder.encode(value, StandardCharsets.UTF_8);
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
