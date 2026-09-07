package com.agentcrawler.crawler.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateRendererTest {

    @Test
    void renderEncodedReplacesKeyword() {
        String url = TemplateRenderer.renderEncoded(
                "https://example.com/s----------.html?wd=@keyword",
                Map.of("keyword", "鬼灭之刃")
        );
        assertTrue(url.contains("wd="));
        assertTrue(url.contains("s----------.html"));
    }

    @Test
    void renderRejectsNullTemplate() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> TemplateRenderer.renderEncoded(null, Map.of("keyword", "x"))
        );
        assertEquals("站点规则缺少 searchURL，无法构造搜索地址", ex.getMessage());
    }
}
