package com.agentcrawler.config;

import com.agentcrawler.crawler.http.SiteHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CrawlerConfig {
    @Bean
    SiteHttpClient siteHttpClient(AppProperties properties) {
        return new SiteHttpClient(properties.crawler().requestTimeoutSeconds());
    }
}
