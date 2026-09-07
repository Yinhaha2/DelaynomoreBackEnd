package com.agentcrawler.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AgentAssemblyConfigTest {

    @Autowired
    private ChatMemoryProvider chatMemoryProvider;

    @Test
    void chatMemoryProviderIsRegistered() {
        assertNotNull(chatMemoryProvider);
        assertNotNull(chatMemoryProvider.get("conv_test"));
    }
}
