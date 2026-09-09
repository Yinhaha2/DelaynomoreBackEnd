package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlResultBufferTest {

    @AfterEach
    void tearDown() {
        CrawlResultBuffer.clear("conv-1");
        CrawlResultBuffer.clear("conv-2");
    }

    @Test
    void pollReturnsQueuedResultForSession() {
        CrawlResourceResult first = CrawlResourceResult.failed("a", "DM84", "DM84", "e1");
        CrawlResourceResult second = CrawlResourceResult.failed("b", "DM84", "DM84", "e2");
        CrawlResultBuffer.push("conv-1", first);
        CrawlResultBuffer.push("conv-1", second);

        assertThat(CrawlResultBuffer.poll("conv-1")).isSameAs(first);
        assertThat(CrawlResultBuffer.poll("conv-1")).isSameAs(second);
        assertThat(CrawlResultBuffer.poll("conv-1")).isNull();
    }

    @Test
    void clearDropsPendingResults() {
        CrawlResultBuffer.push("conv-2", CrawlResourceResult.failed("x", "DM84", "DM84", "e"));
        CrawlResultBuffer.clear("conv-2");
        assertThat(CrawlResultBuffer.poll("conv-2")).isNull();
    }
}
