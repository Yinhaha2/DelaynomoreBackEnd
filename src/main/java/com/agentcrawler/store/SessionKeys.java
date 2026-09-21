package com.agentcrawler.store;

public final class SessionKeys {
    private SessionKeys() {}

    public static String meta(String prefix, String conversationId) {
        return prefix + ":meta:" + conversationId;
    }

    public static String board(String prefix, String conversationId) {
        return prefix + ":board:" + conversationId;
    }

    public static String memory(String prefix, String conversationId) {
        return prefix + ":mem:" + conversationId;
    }

    public static String conversationIdOf(Object memoryId) {
        return memoryId == null ? "" : String.valueOf(memoryId);
    }
}
