package com.agentcrawler.link;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoSiteParserTest {

    @Test
    void extractsBilibiliBv() {
        VideoSiteParser.VideoSiteHint hint = VideoSiteParser.parse(
                "https://www.bilibili.com/video/BV1xx411c7mD?spm_id_from=333"
        );
        assertThat(hint.site()).isEqualTo("bilibili");
        assertThat(hint.mediaId()).isEqualTo("BV1xx411c7mD");
    }

    @Test
    void extractsYoutubeId() {
        VideoSiteParser.VideoSiteHint hint = VideoSiteParser.parse("https://youtu.be/dQw4w9wgwcv");
        assertThat(hint.site()).isEqualTo("youtube");
        assertThat(hint.mediaId()).isEqualTo("dQw4w9wgwcv");
    }
}
