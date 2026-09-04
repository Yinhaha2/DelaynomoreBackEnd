package com.agentcrawler.agent.langchain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlIntentParserTest {
    @Test
    void parsesQuotedTitleAndDefaultSite() {
        var intent = CrawlIntentParser.parse("帮我找《葬送的芙莉莲》的播放资源");
        assertTrue(intent.isPresent());
        assertEquals("葬送的芙莉莲", intent.get().keyword());
        assertEquals("DM84", intent.get().site());
    }

    @Test
    void parsesSiteUrl() {
        var intent = CrawlIntentParser.parse("在 https://dmbus.cc/ 搜索《间谍过家家》");
        assertTrue(intent.isPresent());
        assertEquals("间谍过家家", intent.get().keyword());
        assertEquals("https://dmbus.cc/", intent.get().site());
    }
}
