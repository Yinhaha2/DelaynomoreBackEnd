package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 暂存爬虫全量结果（含真实 URL），供 SSE 下发；不进入 LLM 上下文。
 */
public final class CrawlResultBuffer {
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<CrawlResourceResult>> BY_SESSION =
            new ConcurrentHashMap<>();
    private static final ThreadLocal<ConcurrentLinkedQueue<CrawlResourceResult>> LOCAL =
            ThreadLocal.withInitial(ConcurrentLinkedQueue::new);

    private CrawlResultBuffer() {}

    public static void push(String sessionId, CrawlResourceResult result) {
        if (result == null) {
            return;
        }
        LOCAL.get().add(result);
        if (sessionId != null && !sessionId.isBlank()) {
            BY_SESSION.computeIfAbsent(sessionId, key -> new ConcurrentLinkedQueue<>()).add(result);
        }
    }

    public static CrawlResourceResult poll(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            ConcurrentLinkedQueue<CrawlResourceResult> queue = BY_SESSION.get(sessionId);
            if (queue != null) {
                CrawlResourceResult result = queue.poll();
                if (result != null) {
                    LOCAL.get().remove(result);
                    return result;
                }
            }
        }
        return LOCAL.get().poll();
    }

    public static void clear(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            BY_SESSION.remove(sessionId);
        }
        LOCAL.remove();
    }
}
