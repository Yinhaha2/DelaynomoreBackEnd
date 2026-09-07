package com.agentcrawler.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvFileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void parseIgnoresCommentsAndStripsQuotes() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                # comment
                DEEPSEEK_API_KEY="sk-test-key"
                DEEPSEEK_MODEL=deepseek-chat
                export AGENT_UPLOAD_DIR='./data'
                """);

        Map<String, String> values = EnvFileLoader.parse(env);

        assertEquals("sk-test-key", values.get("DEEPSEEK_API_KEY"));
        assertEquals("deepseek-chat", values.get("DEEPSEEK_MODEL"));
        assertEquals("./data", values.get("AGENT_UPLOAD_DIR"));
        assertTrue(values.size() >= 3);
    }
}
