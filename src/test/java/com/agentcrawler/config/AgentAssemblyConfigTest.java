package com.agentcrawler.config;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AgentAssemblyConfigTest {

    @Autowired
    private ChatMemoryProvider chatMemoryProvider;

    @Autowired
    private ChatMemoryStore chatMemoryStore;

    @Test
    void chatMemoryProviderIsRegisteredAndBackedByStore() {
        assertNotNull(chatMemoryProvider);
        assertNotNull(chatMemoryProvider.get("conv_test"));
        assertNotNull(chatMemoryStore);
        chatMemoryStore.updateMessages("conv_test", List.of(UserMessage.from("第二季呢")));
        assertEquals(1, chatMemoryStore.getMessages("conv_test").size());
    }
}
