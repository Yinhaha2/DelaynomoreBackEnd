package com.agentcrawler.store;

import com.agentcrawler.config.SessionProperties;
import com.agentcrawler.crawler.cache.RemoteKvStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Conversation meta: local map + optional Redis {@code asess:meta:*}. Redis outages stay local.
 */
@Component
public class PersistentConversationStore implements ConversationStore {
    private static final Logger log = LoggerFactory.getLogger(PersistentConversationStore.class);

    private final ConversationStore local;
    private final RemoteKvStore remote;
    private final SessionProperties properties;
    private final ObjectMapper mapper;

    @Autowired
    public PersistentConversationStore(RemoteKvStore remote, SessionProperties properties) {
        this(new InMemoryConversationStore(), remote, properties, SessionJson.mapper());
    }

    public PersistentConversationStore(
            ConversationStore local,
            RemoteKvStore remote,
            SessionProperties properties,
            ObjectMapper mapper
    ) {
        this.local = local == null ? new InMemoryConversationStore() : local;
        this.remote = remote == null ? RemoteKvStore.noop() : remote;
        this.properties = properties == null ? SessionProperties.localOnly() : properties;
        this.mapper = mapper == null ? SessionJson.mapper() : mapper;
    }

    @Override
    public ConversationRecord create(String id, Instant createdAt) {
        ConversationRecord record = local.create(id, createdAt);
        writeRemote(record);
        return record;
    }

    @Override
    public ConversationRecord find(String id) {
        ConversationRecord localHit = local.find(id);
        if (localHit != null) {
            return localHit;
        }
        ConversationRecord remoteHit = readRemote(id);
        if (remoteHit != null) {
            local.save(remoteHit);
        }
        return remoteHit;
    }

    @Override
    public void save(ConversationRecord record) {
        local.save(record);
        writeRemote(record);
    }

    @Override
    public void touch(String id) {
        ConversationRecord existing = find(id);
        if (existing == null) {
            return;
        }
        ConversationRecord updated = existing.touched();
        local.save(updated);
        if (!remoteEnabled()) {
            return;
        }
        writeRemote(updated);
        try {
            Duration ttl = ttl();
            remote.expire(SessionKeys.board(properties.keyPrefix(), id), ttl);
            remote.expire(SessionKeys.memory(properties.keyPrefix(), id), ttl);
        } catch (RuntimeException ex) {
            log.warn("刷新会话 TTL 失败 {}: {}", id, ex.toString());
        }
    }

    private ConversationRecord readRemote(String id) {
        if (!remoteEnabled() || id == null || id.isBlank()) {
            return null;
        }
        try {
            String json = remote.get(SessionKeys.meta(properties.keyPrefix(), id));
            if (json == null || json.isBlank()) {
                return null;
            }
            return mapper.readValue(json, ConversationRecord.class);
        } catch (Exception ex) {
            log.warn("读取会话 meta 失败 {}: {}", id, ex.toString());
            return null;
        }
    }

    private void writeRemote(ConversationRecord record) {
        if (!remoteEnabled() || record == null || record.id() == null) {
            return;
        }
        try {
            remote.set(
                    SessionKeys.meta(properties.keyPrefix(), record.id()),
                    mapper.writeValueAsString(record),
                    ttl()
            );
        } catch (Exception ex) {
            log.warn("写入会话 meta 失败 {}: {}", record.id(), ex.toString());
        }
    }

    private boolean remoteEnabled() {
        return properties.enabled();
    }

    private Duration ttl() {
        return Duration.ofSeconds(properties.ttlSeconds());
    }
}
