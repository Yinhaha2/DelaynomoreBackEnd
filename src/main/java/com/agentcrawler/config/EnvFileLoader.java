package com.agentcrawler.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 Spring 启动前从项目根目录 {@code .env} 加载配置到 System properties。
 * 环境变量已存在时不覆盖；找不到文件则静默跳过。
 */
public final class EnvFileLoader {
    private EnvFileLoader() {}

    public static void load() {
        Path envFile = locateEnvFile();
        if (envFile == null) {
            System.out.println("[env] 未找到 .env（已搜索 user.dir 及上级目录）");
            return;
        }

        Map<String, String> values;
        try {
            values = parse(envFile);
        } catch (IOException ex) {
            System.err.println("[env] 读取失败: " + envFile.toAbsolutePath() + " — " + ex.getMessage());
            return;
        }

        int applied = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (hasEnv(key)) {
                continue;
            }
            System.setProperty(key, value);
            applied++;
        }

        String apiKey = firstNonBlank(
                System.getenv("DEEPSEEK_API_KEY"),
                System.getProperty("DEEPSEEK_API_KEY")
        );
        if (apiKey != null && !apiKey.isBlank()) {
            // 直接写入 Spring 绑定路径，避免仅依赖占位符解析
            if (!hasEnv("agent.llm.api-key") && isBlank(System.getProperty("agent.llm.api-key"))) {
                System.setProperty("agent.llm.api-key", apiKey);
            }
            System.out.println("[env] 已从 " + envFile.toAbsolutePath()
                    + " 加载配置（写入 " + applied + " 项），DEEPSEEK_API_KEY=" + mask(apiKey));
        } else {
            System.out.println("[env] 已读取 " + envFile.toAbsolutePath()
                    + "（写入 " + applied + " 项），但未找到 DEEPSEEK_API_KEY");
        }
    }

    static Path locateEnvFile() {
        List<Path> candidates = new ArrayList<>();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve(".env"));

        Path walk = cwd;
        for (int i = 0; i < 4 && walk != null; i++) {
            candidates.add(walk.resolve(".env"));
            walk = walk.getParent();
        }

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        }
        return null;
    }

    static Map<String, String> parse(Path envFile) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = stripBom(raw).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.regionMatches(true, 0, "export ", 0, 7)) {
                line = line.substring(7).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = stripQuotes(line.substring(eq + 1).trim());
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static boolean hasEnv(String key) {
        String value = System.getenv(key);
        return value != null && !value.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String stripBom(String value) {
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String mask(String apiKey) {
        if (apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
