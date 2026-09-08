package com.agentcrawler.crawler.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlNavigationTest {

    @Test
    void followsMetaRefresh() {
        String next = HtmlNavigation.nextLocation(
                "<html><head><meta http-equiv=\"refresh\" content=\"0;url=/show/1\"></head></html>",
                "http://example.test/search"
        ).orElseThrow();
        assertEquals("http://example.test/show/1", next);
    }

    @Test
    void followsVerifiedSuccessPage() {
        String next = HtmlNavigation.nextLocation(
                "<html>You have verified successfully <a href=\"/home\">continue</a></html>",
                "http://example.test/gate"
        ).orElseThrow();
        assertEquals("http://example.test/home", next);
    }

    @Test
    void ignoresNormalHtml() {
        assertTrue(HtmlNavigation.nextLocation("<html><body>ok</body></html>", "http://example.test/").isEmpty());
    }
}
