package com.agentcrawler.streaming;

import com.agentcrawler.agent.session.SessionContextHolder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 把检索过程写进当前会话的思考流。工具线程拿不到 ThreadLocal 时，
 * 若只有一路会话在跑，就记到这一路上。
 */
public final class ThinkingReporter {
    /** 同一条进度连续空转这么多次后，由生成循环停止检索。 */
    public static final int MAX_IDLE_REPEATS = 5;

    private static final ConcurrentHashMap<String, Sink> SINKS = new ConcurrentHashMap<>();

    private ThinkingReporter() {}

    public static void bind(String conversationId, Consumer<String> consumer) {
        if (conversationId == null || conversationId.isBlank() || consumer == null) {
            return;
        }
        SINKS.put(conversationId, new Sink(consumer));
    }

    public static void clear(String conversationId) {
        if (conversationId != null) {
            SINKS.remove(conversationId);
        }
    }

    public static void ensureSession() {
        if (SessionContextHolder.get() != null) {
            return;
        }
        if (SINKS.size() != 1) {
            return;
        }
        String only = SINKS.keySet().iterator().next();
        SessionContextHolder.set(only);
    }

    public static void note(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        Sink sink = resolve();
        if (sink != null) {
            sink.accept(line);
        }
    }

    /**
     * 长时间没有新进度时补一句「仍在进行」。
     *
     * @return 当前这条进度已经连续空转的次数；未到间隔时返回 0
     */
    public static void touch(String conversationId) {
        Sink sink = conversationId == null ? null : SINKS.get(conversationId);
        if (sink != null) {
            sink.touch();
        }
    }

    public static int nudgeIdle(String conversationId, long idleMs) {
        Sink sink = conversationId == null ? null : SINKS.get(conversationId);
        if (sink == null) {
            return 0;
        }
        return sink.nudgeIfIdle(idleMs);
    }

    private static Sink resolve() {
        String id = SessionContextHolder.get();
        if (id != null) {
            Sink sink = SINKS.get(id);
            if (sink != null) {
                return sink;
            }
        }
        if (SINKS.size() == 1) {
            return SINKS.values().iterator().next();
        }
        return null;
    }

    private static final class Sink {
        private final Consumer<String> consumer;
        private String stage = "正在处理";
        private long updatedAt = System.currentTimeMillis();
        private int idleRepeats;

        private Sink(Consumer<String> consumer) {
            this.consumer = consumer;
        }

        private synchronized void accept(String line) {
            String normalized = line.strip();
            if (normalized.isEmpty()) {
                return;
            }
            stage = normalized;
            updatedAt = System.currentTimeMillis();
            idleRepeats = 0;
            consumer.accept(normalized + "\n");
        }

        private synchronized void touch() {
            updatedAt = System.currentTimeMillis();
            idleRepeats = 0;
        }

        private synchronized int nudgeIfIdle(long idleMs) {
            long now = System.currentTimeMillis();
            if (now - updatedAt < idleMs) {
                return 0;
            }
            updatedAt = now;
            idleRepeats++;
            consumer.accept("仍在进行：" + stage + "\n");
            return idleRepeats;
        }
    }
}
