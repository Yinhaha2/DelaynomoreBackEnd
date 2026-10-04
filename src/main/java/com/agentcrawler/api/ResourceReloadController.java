package com.agentcrawler.api;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import com.agentcrawler.model.ReloadResourceRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceReloadController {
    private static final Logger log = LoggerFactory.getLogger(ResourceReloadController.class);

    private final ResourceCrawlerService crawler;

    public ResourceReloadController(ResourceCrawlerService crawler) {
        this.crawler = crawler;
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload(@Valid @RequestBody ReloadResourceRequest request) {
        try {
            CrawlResourceResult result = crawler.reload(request.keyword(), request.site());
            return ResponseEntity.ok(ReloadResourcePayload.from(result));
        } catch (RuntimeException ex) {
            log.warn("重新抓取失败: {}", ex.toString());
            return ResponseEntity.ok(ReloadResourcePayload.failed(
                    request.keyword(),
                    request.site(),
                    ResourceCrawlerService.userFacingMessage(ex)
            ));
        }
    }
}
