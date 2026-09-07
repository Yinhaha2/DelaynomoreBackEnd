package com.agentcrawler;

import com.agentcrawler.config.EnvFileLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentCrawlerApplication {
    public static void main(String[] args) {
        EnvFileLoader.load();
        SpringApplication.run(AgentCrawlerApplication.class, args);
    }
}
