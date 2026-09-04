package com.agentcrawler.config;

import com.agentcrawler.agent.AgentHandler;
import com.agentcrawler.agent.langchain.LangChainStreamingAgent;
import com.agentcrawler.agent.langchain.ResourceCrawlTools;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {
    @Bean
    ResourceCrawlTools resourceCrawlTools(
            ResourceCrawlerService crawlerService,
            ObjectMapper objectMapper
    ) {
        return new ResourceCrawlTools(crawlerService, objectMapper);
    }

    @Bean
    AgentHandler agentHandler(LangChainStreamingAgent langChainStreamingAgent) {
        return langChainStreamingAgent;
    }
}
