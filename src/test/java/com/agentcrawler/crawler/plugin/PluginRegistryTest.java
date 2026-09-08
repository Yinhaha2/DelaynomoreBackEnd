package com.agentcrawler.crawler.plugin;

import com.agentcrawler.crawler.model.PluginRule;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryTest {

    @Test
    void loadsKazumiCamelCaseIncludingUseWebview() throws Exception {
        PluginRegistry registry = new PluginRegistry();
        registry.loadPlugins();
        PluginRule dm84 = registry.resolve("DM84");
        assertTrue(dm84.isUseWebview());
        assertEquals("//div/div[3]/ul/li", dm84.getSearchList());
        assertTrue(dm84.getSearchURL().contains("@keyword"));
        assertTrue(dm84.getBaseURL().contains("dmbus.cc"));
    }

    @Test
    void httpSnakeCaseMapperDoesNotBindKazumiSearchList() throws Exception {
        ObjectMapper snake = new ObjectMapper();
        snake.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        snake.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream in = getClass().getResourceAsStream("/plugins/DM84.json")) {
            assertNotNull(in);
            PluginRule broken = snake.readValue(in, PluginRule.class);
            assertNull(broken.getSearchList());
        }
    }
}
