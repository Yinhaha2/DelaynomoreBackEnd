package com.agentcrawler.crawler.reliability;

public final class UpstreamKeys {
    private UpstreamKeys() {}

    public static String plugin(String name) {
        return "plugin:" + normalize(name);
    }

    public static String fallback(String name) {
        return "fallback:" + normalize(name);
    }

    public static boolean isPlugin(String key) {
        return key != null && key.startsWith("plugin:");
    }

    public static boolean isFallback(String key) {
        return key != null && key.startsWith("fallback:");
    }

    public static String nameOf(String key) {
        if (key == null) {
            return "";
        }
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(colon + 1);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim();
    }
}
