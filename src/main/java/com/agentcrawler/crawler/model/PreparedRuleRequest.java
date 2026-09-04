package com.agentcrawler.crawler.model;

import java.util.Map;

public record PreparedRuleRequest(
        String method,
        String url,
        Map<String, String> headers,
        Map<String, String> query,
        String bodyType,
        Object body
) {}
