package com.agentcrawler.agent.session;

import com.agentcrawler.config.SessionProperties;
import com.agentcrawler.crawler.cache.RemoteKvStore;
import com.agentcrawler.store.SessionJson;
import com.agentcrawler.store.SessionKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionBlackboardStore {
    private static final Logger log = LoggerFactory.getLogger(SessionBlackboardStore.class);

    private final Map<String, AnimeSessionState> states = new ConcurrentHashMap<>();
    private final RemoteKvStore remote;
    private final SessionProperties properties;
    private final ObjectMapper mapper;

    public SessionBlackboardStore() {
        this(RemoteKvStore.noop(), SessionProperties.localOnly(), SessionJson.mapper());
    }

    @Autowired
    public SessionBlackboardStore(RemoteKvStore remote, SessionProperties properties) {
        this(remote, properties, SessionJson.mapper());
    }

    public SessionBlackboardStore(RemoteKvStore remote, SessionProperties properties, ObjectMapper mapper) {
        this.remote = remote == null ? RemoteKvStore.noop() : remote;
        this.properties = properties == null ? SessionProperties.localOnly() : properties;
        this.mapper = mapper == null ? SessionJson.mapper() : mapper;
    }

    public AnimeSessionState getOrCreate(String sessionId) {
        AnimeSessionState existing = get(sessionId);
        if (existing != null) {
            return existing;
        }
        AnimeSessionState created = new AnimeSessionState();
        if (sessionId != null && !sessionId.isBlank()) {
            states.put(sessionId, created);
        }
        return created;
    }

    public AnimeSessionState get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        AnimeSessionState local = states.get(sessionId);
        if (local != null) {
            return local;
        }
        AnimeSessionState loaded = readRemote(sessionId);
        if (loaded != null) {
            states.put(sessionId, loaded);
        }
        return loaded;
    }

    public void save(String sessionId, AnimeSessionState state) {
        if (sessionId == null || sessionId.isBlank() || state == null) {
            return;
        }
        states.put(sessionId, state);
        writeRemote(sessionId, state);
    }

    public void remove(String sessionId) {
        clear(sessionId);
    }

    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        states.remove(sessionId);
        if (properties.enabled()) {
            try {
                remote.delete(SessionKeys.board(properties.keyPrefix(), sessionId));
            } catch (RuntimeException ex) {
                log.warn("删除会话黑板失败 {}: {}", sessionId, ex.toString());
            }
        }
    }

    private AnimeSessionState readRemote(String sessionId) {
        if (!properties.enabled()) {
            return null;
        }
        try {
            String json = remote.get(SessionKeys.board(properties.keyPrefix(), sessionId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return mapper.readValue(json, AnimeSessionState.class);
        } catch (Exception ex) {
            log.warn("读取会话黑板失败 {}: {}", sessionId, ex.toString());
            return null;
        }
    }

    private void writeRemote(String sessionId, AnimeSessionState state) {
        if (!properties.enabled()) {
            return;
        }
        try {
            remote.set(
                    SessionKeys.board(properties.keyPrefix(), sessionId),
                    mapper.writeValueAsString(state),
                    Duration.ofSeconds(properties.ttlSeconds())
            );
        } catch (Exception ex) {
            log.warn("写入会话黑板失败 {}: {}", sessionId, ex.toString());
        }
    }
}
