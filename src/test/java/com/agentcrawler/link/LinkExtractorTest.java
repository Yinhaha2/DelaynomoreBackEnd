package com.agentcrawler.link;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkExtractorTest {

    @Test
    void extractsHttpUrlWithQueryAndChinesePunctuation() {
        List<ExtractedLink> links = LinkExtractor.extract(
                "帮我看看这个https://bangumi.tv/subject/328609?from=share。能看吗"
        );

        assertThat(links).hasSize(1);
        assertThat(links.get(0).raw()).isEqualTo("https://bangumi.tv/subject/328609?from=share");
        assertThat(links.get(0).kind()).isEqualTo(LinkKind.WEB_PAGE);
    }

    @Test
    void extractsMagnetAndHttpTogether() {
        List<ExtractedLink> links = LinkExtractor.extract(
                "这个 magnet:?xt=urn:btih:abcdef1234567890&dn=Frieren 或者 https://b23.tv/abc123 哪个能放"
        );

        assertThat(links).extracting(ExtractedLink::kind)
                .contains(LinkKind.MAGNET, LinkKind.SHORT_LINK);
    }

    @Test
    void classifiesBilibiliAndStream() {
        assertThat(UrlClassifier.classify("https://www.bilibili.com/video/BV1xx411c7mD"))
                .isEqualTo(LinkKind.VIDEO_SITE);
        assertThat(UrlClassifier.classify("https://cdn.example.com/ep1/index.m3u8"))
                .isEqualTo(LinkKind.STREAM);
    }
}
