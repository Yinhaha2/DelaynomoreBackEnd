package com.agentcrawler.link;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenGraphParserTest {

    @Test
    void readsOgTitleAndDescription() {
        String html = """
                <html><head>
                <meta property="og:title" content="《葬送的芙莉莲》第5话 - 哔哩哔哩">
                <meta property="og:description" content="法师的旅途">
                <meta property="og:image" content="https://cdn.example.com/cover.jpg">
                <title>fallback</title>
                </head></html>
                """;

        OpenGraphSnapshot snapshot = OpenGraphParser.parse(html);

        assertThat(snapshot.title()).contains("葬送的芙莉莲");
        assertThat(snapshot.description()).isEqualTo("法师的旅途");
        assertThat(snapshot.image()).isEqualTo("https://cdn.example.com/cover.jpg");
    }
}
