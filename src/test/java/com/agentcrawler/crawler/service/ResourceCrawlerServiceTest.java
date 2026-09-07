package com.agentcrawler.crawler.service;

import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResourceCrawlerServiceTest {

    @Test
    void userFacingMessageHidesHttp522Url() {
        String message = ResourceCrawlerService.userFacingMessage(
                new AppException(ErrorCode.CRAWL_FAILED, "搜索失败: HTTP 522 for https://dmbus.cc/s----------.html?wd=x")
        );
        assertEquals("检索站点暂时无法访问，请稍后再试。", message);
        assertFalse(message.contains("http"));
        assertFalse(message.contains("522"));
    }

    @Test
    void userFacingMessageHidesNullTextNpe() {
        String message = ResourceCrawlerService.userFacingMessage(
                new NullPointerException("Cannot invoke \"java.lang.CharSequence.length()\" because \"this.text\" is null")
        );
        assertEquals("当前站点规则不可用，请稍后再试。", message);
    }

    @Test
    void userFacingMessageHidesTimeout() {
        String message = ResourceCrawlerService.userFacingMessage(
                new IOException("HTTP 504 for https://example.com")
        );
        assertEquals("检索站点响应超时，请稍后再试。", message);
    }
}
