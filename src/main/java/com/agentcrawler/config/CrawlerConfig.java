package com.agentcrawler.config;

import com.agentcrawler.crawler.http.SiteHttpClient;
import com.agentcrawler.link.LinkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CrawlerConfig {
    @Bean
    SiteHttpClient siteHttpClient(AppProperties properties) {
        return new SiteHttpClient(properties.crawler().requestTimeoutSeconds());
    }

    @Bean
    LinkHttpClient linkHttpClient(AppProperties properties) {
        return new LinkHttpClient(properties.link().timeoutSeconds());
    }
}
