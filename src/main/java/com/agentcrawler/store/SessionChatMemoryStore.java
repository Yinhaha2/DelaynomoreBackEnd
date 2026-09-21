package com.agentcrawler.store;

import com.agentcrawler.config.SessionProperties;
import com.agentcrawler.crawler.cache.RemoteKvStore;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j ChatMemory L1 + optional Redis {@code asess:mem:*}. Never stores crawler play URLs.
 */
@Component
public class SessionChatMemoryStore implements ChatMemoryStore {
    private static final Logger log = LoggerFactory.getLogger(SessionChatMemoryStore.class);

    private final Map<String, List<ChatMessage>> local = new ConcurrentHashMap<>();
    private final RemoteKvStore remote;
    private final SessionProperties properties;

    @Autowired
    public SessionChatMemoryStore(RemoteKvStore remote, SessionProperties properties) {
        this.remote = remote == null ? RemoteKvStore.noop() : remote;
        this.properties = properties == null ? SessionProperties.localOnly() : properties;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String id = SessionKeys.conversationIdOf(memoryId);
        if (id.isBlank()) {
            return new ArrayList<>();
        }
        List<ChatMessage> cached = local.get(id);
        if (cached != null) {
            return copy(cached);
        }
        List<ChatMessage> loaded = readRemote(id);
        if (loaded != null) {
            local.put(id, loaded);
            return copy(loaded);
        }
        return new ArrayList<>();
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id = SessionKeys.conversationIdOf(memoryId);
        if (id.isBlank()) {
            return;
        }
        List<ChatMessage> snapshot = copy(messages);
        local.put(id, snapshot);
        writeRemote(id, snapshot);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String id = SessionKeys.conversationIdOf(memoryId);
        if (id.isBlank()) {
            return;
        }
        local.remove(id);
        if (properties.enabled()) {
            try {
                remote.delete(SessionKeys.memory(properties.keyPrefix(), id));
            } catch (RuntimeException ex) {
                log.warn("删除 ChatMemory 失败 {}: {}", id, ex.toString());
            }
        }
    }

    private List<ChatMessage> readRemote(String id) {
        if (!properties.enabled()) {
            return null;
        }
        try {
            String json = remote.get(SessionKeys.memory(properties.keyPrefix(), id));
            if (json == null || json.isBlank()) {
                return null;
            }
            List<ChatMessage> parsed = ChatMessageDeserializer.messagesFromJson(json);
            return parsed == null ? List.of() : new ArrayList<>(parsed);
        } catch (Exception ex) {
            log.warn("读取 ChatMemory 失败 {}: {}", id, ex.toString());
            return null;
        }
    }

    private void writeRemote(String id, List<ChatMessage> messages) {
        if (!properties.enabled()) {
            return;
        }
        try {
            remote.set(
                    SessionKeys.memory(properties.keyPrefix(), id),
                    ChatMessageSerializer.messagesToJson(messages == null ? List.of() : messages),
                    Duration.ofSeconds(properties.ttlSeconds())
            );
        } catch (Exception ex) {
            log.warn("写入 ChatMemory 失败 {}: {}", id, ex.toString());
        }
    }

    private static List<ChatMessage> copy(List<ChatMessage> messages) {
        return messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }
}
