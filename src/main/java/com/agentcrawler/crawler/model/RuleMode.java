package com.agentcrawler.crawler.model;

public final class RuleMode {
    public static final String XPATH = "xpath";
    public static final String API = "api";

    private RuleMode() {}

    public static String normalize(String value) {
        return API.equalsIgnoreCase(value) ? API : XPATH;
    }

    public static boolean isApi(String value) {
        return API.equalsIgnoreCase(value);
    }
}
