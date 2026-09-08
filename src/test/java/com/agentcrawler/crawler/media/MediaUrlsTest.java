package com.agentcrawler.crawler.media;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaUrlsTest {

    @Test
    void unwrapsUrlQueryAndChangePlay() {
        Set<String> urls = new LinkedHashSet<>();
        MediaUrls.harvest("https://player.example/jump?url=https%3A%2F%2Fcdn.example%2Fa.m3u8", urls);
        MediaUrls.harvest("changeplay('https://cdn.example/b.mp4$mp4');", urls);
        assertTrue(urls.contains("https://cdn.example/a.m3u8"));
        assertTrue(urls.contains("https://cdn.example/b.mp4"));
    }
}
