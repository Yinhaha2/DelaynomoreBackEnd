package com.agentcrawler.crawler.reliability;

import com.agentcrawler.crawler.model.CrawlResourceResult;

/**
 * 便宜的播放地址探测。确认失效后才值得整页重爬。
 */
public interface PlaybackProbe {
    enum Verdict {
        FRESH,
        STALE,
        UNKNOWN
    }

    Verdict probe(CrawlResourceResult result);
}
