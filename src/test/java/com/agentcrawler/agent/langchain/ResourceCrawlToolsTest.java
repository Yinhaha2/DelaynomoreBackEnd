package com.agentcrawler.agent.langchain;

import com.agentcrawler.agent.session.SessionContextHolder;
import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceCrawlToolsTest {

    @AfterEach
    void tearDown() {
        SessionContextHolder.clear();
        CrawlResultBuffer.clear("conv-tools");
    }

    @Test
    void searchResourcesReturnsSummaryAndBuffersFullResult() {
        ResourceCrawlerService crawler = mock(ResourceCrawlerService.class);
        when(crawler.crawl(anyString(), anyString())).thenReturn(new CrawlResourceResult(
                "鬼灭之刃",
                "SiliSili",
                "SiliSili",
                List.of(new CrawlResourceResult.VideoResource(
                        "第01集",
                        "https://silivideo.example.r2.cloudflarestorage.com/ep1.mp4?X-Amz-Signature=secret",
                        "https://example.com/play/1",
                        null
                )),
                List.of(),
                List.of()
        ));
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        ResourceCrawlTools tools = new ResourceCrawlTools(crawler, mapper);
        SessionContextHolder.set("conv-tools");

        String json = tools.searchResources("鬼灭之刃", "SiliSili");
        CrawlResourceResult buffered = CrawlResultBuffer.poll("conv-tools");

        assertThat(json).contains("\"ok\":true");
        assertThat(json).contains("\"episode_count\":1");
        assertThat(json).doesNotContain("https://");
        assertThat(json).doesNotContain("X-Amz-Signature");
        assertThat(buffered).isNotNull();
        assertThat(buffered.videos()).hasSize(1);
        assertThat(buffered.videos().get(0).url()).contains("X-Amz-Signature");
    }
}
