package com.agentcrawler.agent.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionBlackboardStore {
    private final Map<String, AnimeSessionState> states = new ConcurrentHashMap<>();

    public AnimeSessionState getOrCreate(String sessionId) {
        return states.computeIfAbsent(sessionId, ignored -> new AnimeSessionState());
    }

    public AnimeSessionState get(String sessionId) {
        return states.get(sessionId);
    }

    public void remove(String sessionId) {
        states.remove(sessionId);
    }

    public void clear(String sessionId) {
        states.remove(sessionId);
    }
}
