package com.agentcrawler.core;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class IdGenerator {
    private IdGenerator() {}

    public static String conversationId() {
        return "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String messageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String imageId() {
        return "img_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String nowIso() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
