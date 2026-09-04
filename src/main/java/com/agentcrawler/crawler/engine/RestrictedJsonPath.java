package com.agentcrawler.crawler.engine;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RestrictedJsonPath {
    private static final Configuration CONFIG = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS);

    private RestrictedJsonPath() {}

    public static void validate(String expression) {
        if (expression == null || expression.isBlank() || !expression.startsWith("$")) {
            throw new ApiRuleFormatException("JSONPath 必须以 $ 开头: " + expression);
        }
        int index = 1;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (current == '.') {
                index++;
                int start = index;
                while (index < expression.length() && isPathChar(expression.charAt(index))) {
                    index++;
                }
                if (index == start) {
                    throw new ApiRuleFormatException("不支持的 JSONPath: " + expression);
                }
                continue;
            }
            if (current == '[') {
                int end = findBracketEnd(expression, index);
                String content = expression.substring(index + 1, end).trim();
                boolean isIndex = content.matches("\\d+");
                boolean isWildcard = "*".equals(content);
                boolean isQuoted = content.length() >= 2
                        && ((content.startsWith("'") && content.endsWith("'"))
                        || (content.startsWith("\"") && content.endsWith("\"")));
                if (!isIndex && !isWildcard && !isQuoted) {
                    throw new ApiRuleFormatException("不支持的 JSONPath 片段: [" + content + "]");
                }
                index = end + 1;
                continue;
            }
            throw new ApiRuleFormatException("不支持的 JSONPath: " + expression);
        }
    }

    public static List<Object> read(Object document, String expression) {
        validate(expression);
        try {
            Object value = JsonPath.using(CONFIG).parse(document).read(expression);
            if (value == null) {
                return List.of();
            }
            if (value instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            return List.of(value);
        } catch (Exception ex) {
            throw new ApiRuleFormatException("JSONPath 解析失败 " + expression + ": " + ex.getMessage());
        }
    }

    public static Object readFirst(Object document, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        List<Object> values = read(document, expression);
        return values.isEmpty() ? null : values.get(0);
    }

    public static DocumentContext parse(Object document) {
        return JsonPath.using(CONFIG).parse(document);
    }

    private static int findBracketEnd(String expression, int start) {
        Character quote = null;
        boolean escaped = false;
        for (int i = start + 1; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (quote != null) {
                if (quote == current) {
                    quote = null;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == ']') {
                return i;
            }
        }
        throw new ApiRuleFormatException("JSONPath 缺少 ]: " + expression);
    }

    private static boolean isPathChar(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '-' || value == '$';
    }
}
