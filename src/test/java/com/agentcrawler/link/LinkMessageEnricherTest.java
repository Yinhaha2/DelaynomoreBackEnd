package com.agentcrawler.link;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkMessageEnricherTest {

    @Test
    void injectsStructuredFactsBeforeUserQuestion() {
        LinkInspectionResult result = new LinkInspectionResult();
        result.setUrl("https://bangumi.tv/subject/1");
        result.setKind(LinkKind.WEB_PAGE);
        result.setWorkTitle("赛博朋克：边缘跑手");
        result.setTitle("赛博朋克：边缘跑手");
        result.setAlive(true);

        String enriched = LinkMessageEnricher.enrich("帮我看看这个能看吗", List.of(result));

        assertThat(enriched).contains("系统前置链接解析");
        assertThat(enriched).contains("赛博朋克：边缘跑手");
        assertThat(enriched).contains("帮我看看这个能看吗");
    }
}
