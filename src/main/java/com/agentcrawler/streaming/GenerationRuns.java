package com.agentcrawler.streaming;

import com.agentcrawler.agent.session.SessionContextHolder;
import okhttp3.Call;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一轮对话生成的取消开关。检索线程和 HTTP 调用靠会话号找到这一轮，
 * 停止时会取消正在进行的站点请求。
 */
public final class GenerationRuns {
    public enum StopReason {
        USER,
        SITE_RETRY_LIMIT
    }

    private static final ConcurrentHashMap<String, GenerationRun> RUNS = new ConcurrentHashMap<>();

    private GenerationRuns() {}

    public static GenerationRun begin(String conversationId) {
        GenerationRun run = new GenerationRun();
        if (conversationId == null || conversationId.isBlank()) {
            return run;
        }
        GenerationRun previous = RUNS.put(conversationId, run);
        if (previous != null) {
            previous.requestStop(StopReason.USER);
        }
        return run;
    }

    public static void end(String conversationId, GenerationRun run) {
        if (conversationId == null || run == null) {
            return;
        }
        RUNS.remove(conversationId, run);
    }

    public static GenerationRun get(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        return RUNS.get(conversationId);
    }

    public static boolean stop(String conversationId, StopReason reason) {
        GenerationRun run = get(conversationId);
        if (run == null) {
            return false;
        }
        return run.requestStop(reason == null ? StopReason.USER : reason);
    }

    public static boolean isStopped(String conversationId) {
        GenerationRun run = get(conversationId);
        return run != null && run.stopped();
    }

    public static GenerationRun current() {
        String id = SessionContextHolder.get();
        if (id != null) {
            GenerationRun run = RUNS.get(id);
            if (run != null) {
                return run;
            }
        }
        if (RUNS.size() == 1) {
            return RUNS.values().iterator().next();
        }
        return null;
    }

    public static void checkpoint() {
        GenerationRun run = current();
        if (run != null && run.stopped()) {
            throw new GenerationStoppedException(run.reason());
        }
    }

    public static final class GenerationRun {
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicReference<Call> call = new AtomicReference<>();
        private volatile StopReason reason = StopReason.USER;

        public boolean stopped() {
            return stopped.get();
        }

        public StopReason reason() {
            return reason;
        }

        public boolean requestStop(StopReason next) {
            reason = next == null ? StopReason.USER : next;
            boolean first = stopped.compareAndSet(false, true);
            Call active = call.get();
            if (active != null) {
                active.cancel();
            }
            return first;
        }

        public void attach(Call next) {
            if (next == null) {
                return;
            }
            call.set(next);
            if (stopped.get()) {
                next.cancel();
            }
        }

        public void detach(Call next) {
            if (next != null) {
                call.compareAndSet(next, null);
            }
        }
    }
}
