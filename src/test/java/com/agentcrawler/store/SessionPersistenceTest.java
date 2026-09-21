package com.agentcrawler.store;

import com.agentcrawler.agent.session.AnimeSessionState;
import com.agentcrawler.agent.session.SessionBlackboardService;
import com.agentcrawler.agent.session.SessionBlackboardStore;
import com.agentcrawler.config.SessionProperties;
import com.agentcrawler.crawler.cache.RemoteKvStore;
import com.agentcrawler.service.ConversationService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SessionPersistenceTest {

    @Test
    void clampsSessionTtlAwayFromPlayUrlRange() {
        SessionProperties tooShort = new SessionProperties(true, 60, 10, "asess");
        SessionProperties tooLong = new SessionProperties(true, 10_000_000, 10, "asess");
        assertThat(tooShort.ttlSeconds()).isEqualTo(3_600);
        assertThat(tooLong.ttlSeconds()).isEqualTo(2_592_000);
    }

    @Test
    void redisKeysStayOnAsessPrefix() {
        assertThat(SessionKeys.meta("asess", "conv_abc")).isEqualTo("asess:meta:conv_abc");
        assertThat(SessionKeys.board("asess", "conv_abc")).isEqualTo("asess:board:conv_abc");
        assertThat(SessionKeys.memory("asess", "conv_abc")).isEqualTo("asess:mem:conv_abc");
    }

    @Test
    void secondInstanceHydratesConversationMetaAndTitle() {
        MapRemoteKvStore remote = new MapRemoteKvStore();
        SessionProperties properties = SessionProperties.localOnly();
        PersistentConversationStore writer = new PersistentConversationStore(
                new InMemoryConversationStore(), remote, properties, SessionJson.mapper()
        );
        ConversationService service = new ConversationService(writer);
        service.createConversation("conv_1");
        service.updateTitle("conv_1", "鬼灭之刃播放资源");

        PersistentConversationStore reader = new PersistentConversationStore(
                new InMemoryConversationStore(), remote, properties, SessionJson.mapper()
        );
        ConversationRecord loaded = reader.find("conv_1");
        assertThat(loaded).isNotNull();
        assertThat(loaded.title()).isEqualTo("鬼灭之刃播放资源");
        assertThat(loaded.id()).isEqualTo("conv_1");
    }

    @Test
    void blackboardSurvivesNewProcessViaRemoteAndTopicSwitchOnlyClearsBoard() {
        MapRemoteKvStore remote = new MapRemoteKvStore();
        SessionProperties properties = SessionProperties.localOnly();
        SessionBlackboardStore writerStore = new SessionBlackboardStore(remote, properties, SessionJson.mapper());
        SessionBlackboardService writer = new SessionBlackboardService(writerStore);
        writer.lockContext("conv_2", "鬼灭之刃", List.of("炭治郎"), "第1集", null, null);

        PersistentConversationStore conversations = new PersistentConversationStore(
                new InMemoryConversationStore(), remote, properties, SessionJson.mapper()
        );
        conversations.create("conv_2", Instant.now());

        SessionBlackboardStore readerStore = new SessionBlackboardStore(remote, properties, SessionJson.mapper());
        SessionBlackboardService reader = new SessionBlackboardService(readerStore);
        AnimeSessionState state = reader.get("conv_2");
        assertThat(state.getWorkTitle()).isEqualTo("鬼灭之刃");
        assertThat(state.isLocked()).isTrue();

        reader.onUserMessage("conv_2", "我们换一部动漫吧");
        assertThat(reader.get("conv_2")).isNull();
        assertThat(conversations.find("conv_2")).isNotNull();
        assertThat(remote.get(SessionKeys.board("asess", "conv_2"))).isNull();
        assertThat(remote.get(SessionKeys.meta("asess", "conv_2"))).isNotBlank();
    }

    @Test
    void chatMemoryRoundTripsThroughRemoteWithoutPlayUrls() {
        MapRemoteKvStore remote = new MapRemoteKvStore();
        SessionChatMemoryStore writer = new SessionChatMemoryStore(remote, SessionProperties.localOnly());
        writer.updateMessages("conv_3", List.of(
                UserMessage.from("帮我找鬼灭"),
                AiMessage.from("已为你检索《鬼灭之刃》的线路。")
        ));

        SessionChatMemoryStore reader = new SessionChatMemoryStore(remote, SessionProperties.localOnly());
        List<ChatMessage> loaded = reader.getMessages("conv_3");
        assertThat(loaded).hasSize(2);
        assertThat(((UserMessage) loaded.get(0)).singleText()).contains("鬼灭");
        assertThat(((AiMessage) loaded.get(1)).text()).contains("鬼灭之刃");
        assertThat(remote.get(SessionKeys.memory("asess", "conv_3"))).doesNotContain("X-Amz-Signature");
        assertThat(remote.get(SessionKeys.memory("asess", "conv_3"))).doesNotContain(".m3u8");
    }

    @Test
    void redisOutageDoesNotFailConversationOrMemory() {
        AtomicInteger ops = new AtomicInteger();
        RemoteKvStore boom = explodingStore(ops);
        SessionProperties properties = SessionProperties.localOnly();
        PersistentConversationStore conversations = new PersistentConversationStore(
                new InMemoryConversationStore(), boom, properties, SessionJson.mapper()
        );
        SessionChatMemoryStore memory = new SessionChatMemoryStore(boom, properties);
        SessionBlackboardStore board = new SessionBlackboardStore(boom, properties, SessionJson.mapper());

        assertThatCode(() -> {
            conversations.create("conv_x", Instant.now());
            conversations.touch("conv_x");
            memory.updateMessages("conv_x", List.of(UserMessage.from("hi")));
            board.save("conv_x", new AnimeSessionState());
            conversations.find("conv_x");
            memory.getMessages("conv_x");
            board.get("conv_x");
        }).doesNotThrowAnyException();
        assertThat(conversations.find("conv_x")).isNotNull();
        assertThat(memory.getMessages("conv_x")).hasSize(1);
    }

    @Test
    void ensureExistsTouchesTtlOnRemoteKeys() {
        MapRemoteKvStore remote = new MapRemoteKvStore();
        PersistentConversationStore store = new PersistentConversationStore(
                new InMemoryConversationStore(), remote, SessionProperties.localOnly(), SessionJson.mapper()
        );
        ConversationService service = new ConversationService(store);
        service.createConversation("conv_ttl");
        remote.expires.set(0);
        service.ensureExists("conv_ttl");
        assertThat(remote.expires.get()).isGreaterThanOrEqualTo(2);
    }

    private static RemoteKvStore explodingStore(AtomicInteger ops) {
        return new RemoteKvStore() {
            @Override
            public String get(String key) {
                ops.incrementAndGet();
                throw new IllegalStateException("redis down");
            }

            @Override
            public void set(String key, String value, Duration ttl) {
                ops.incrementAndGet();
                throw new IllegalStateException("redis down");
            }

            @Override
            public void delete(String key) {
                ops.incrementAndGet();
                throw new IllegalStateException("redis down");
            }

            @Override
            public void expire(String key, Duration ttl) {
                ops.incrementAndGet();
                throw new IllegalStateException("redis down");
            }
        };
    }

    private static final class MapRemoteKvStore implements RemoteKvStore {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final AtomicInteger expires = new AtomicInteger();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public void delete(String key) {
            values.remove(key);
        }

        @Override
        public void expire(String key, Duration ttl) {
            expires.incrementAndGet();
        }
    }
}
