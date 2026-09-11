package com.agentcrawler.config;

import com.agentcrawler.crawler.reliability.CrawlSingleflight;
import com.agentcrawler.crawler.reliability.SiteCircuitBoard;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, CrawlerReliabilityProperties.class})
public class AppConfig {
    @Bean
    SiteCircuitBoard siteCircuitBoard(CrawlerReliabilityProperties reliability) {
        return new SiteCircuitBoard(reliability);
    }

    @Bean
    CrawlSingleflight crawlSingleflight() {
        return new CrawlSingleflight();
    }
}
