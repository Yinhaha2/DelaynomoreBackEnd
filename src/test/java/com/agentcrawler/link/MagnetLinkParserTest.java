package com.agentcrawler.link;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MagnetLinkParserTest {

    @Test
    void parsesHashAndDisplayName() {
        MagnetLinkParser.MagnetInfo info = MagnetLinkParser.parse(
                "magnet:?xt=urn:btih:ABCDEF1234567890&dn=%E8%91%AC%E9%80%81%E7%9A%84%E8%8A%99%E8%8E%89%E8%8E%B2"
        );

        assertThat(info.infoHash()).isEqualTo("abcdef1234567890");
        assertThat(info.displayName()).isEqualTo("葬送的芙莉莲");
    }
}
