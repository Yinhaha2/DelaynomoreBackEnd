package com.agentcrawler.crawler.cache;

import com.agentcrawler.crawler.model.CrawlResourceResult;

public interface CrawlerResultCache {
    CrawlResourceResult getPlay(String lookupKey);

    void putPlay(String lookupKey, CrawlResourceResult result);

    CatalogSnapshot getCatalog(String lookupKey);

    void putCatalog(String lookupKey, CatalogSnapshot snapshot);

    static CrawlerResultCache noop() {
        return new CrawlerResultCache() {
            @Override
            public CrawlResourceResult getPlay(String lookupKey) {
                return null;
            }

            @Override
            public void putPlay(String lookupKey, CrawlResourceResult result) {
            }

            @Override
            public void putCatalog(String lookupKey, CatalogSnapshot snapshot) {
            }

            @Override
            public CatalogSnapshot getCatalog(String lookupKey) {
                return null;
            }
        };
    }
}
