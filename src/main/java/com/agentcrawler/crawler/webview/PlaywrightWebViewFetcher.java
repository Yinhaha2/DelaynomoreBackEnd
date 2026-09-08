package com.agentcrawler.crawler.webview;

import com.agentcrawler.config.AppProperties;
import com.agentcrawler.crawler.media.MediaUrls;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless Chromium fetch for Kazumi {@code useWebview} plugins.
 * Captures rendered HTML plus m3u8/mp4 URLs seen on the network (and {@code player_aaaa.url} if present).
 * Does not hide automation or bypass anti-bot challenges.
 */
@Component
public class PlaywrightWebViewFetcher implements WebViewFetcher {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightWebViewFetcher.class);

    private final AppProperties.Crawler.WebView settings;
    private final Object lock = new Object();

    private volatile boolean started;
    private volatile boolean available;
    private Playwright playwright;
    private Browser browser;

    public PlaywrightWebViewFetcher(AppProperties properties) {
        this.settings = properties.webviewSettings();
    }

    @Override
    public boolean available() {
        return settings.enabled() && available;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!settings.enabled()) {
            return;
        }
        Thread thread = new Thread(this::ensureStarted, "playwright-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public FetchedPage fetch(String url) {
        return fetch(url, Collections.emptyMap());
    }

    @Override
    public FetchedPage fetch(String url, Map<String, String> extraHeaders) {
        if (!available()) {
            throw new IllegalStateException("Playwright WebView is not available");
        }
        synchronized (lock) {
            Page page = browser.newPage();
            Set<String> media = new LinkedHashSet<>();
            try {
                Map<String, String> headers = sanitizeHeaders(extraHeaders);
                if (!headers.isEmpty()) {
                    page.setExtraHTTPHeaders(headers);
                }
                page.onRequest(request -> MediaUrls.harvest(request.url(), media));
                page.onResponse(response -> {
                    MediaUrls.harvest(response.url(), media);
                    if (MediaUrls.isVideoContentType(response.headerValue("content-type"))) {
                        media.add(response.url());
                    }
                });
                int navMs = Math.max(1, settings.navigationTimeoutSeconds()) * 1000;
                page.setDefaultNavigationTimeout(navMs);
                Response response = page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(navMs));
                if (response != null && response.status() >= 400) {
                    throw new IllegalStateException("HTTP " + response.status() + " for " + url);
                }
                waitForSettle(page);
                capturePlayerAaaa(page, media);
                String html = page.content();
                log.info("WebView fetched {} ({} chars, {} media urls)", url, html.length(), media.size());
                return new FetchedPage(html, List.copyOf(media));
            } finally {
                page.close();
            }
        }
    }

    private void waitForSettle(Page page) {
        int settleMs = Math.max(0, settings.settleTimeoutSeconds()) * 1000;
        if (settleMs <= 0) {
            return;
        }
        try {
            page.waitForLoadState(
                    LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(settleMs));
        } catch (RuntimeException e) {
            log.debug("WebView NETWORKIDLE wait ended: {}", e.getMessage());
        }
    }

    private static void capturePlayerAaaa(Page page, Set<String> media) {
        try {
            Object url = page.evaluate("""
                    () => {
                      try {
                        if (typeof player_aaaa !== 'undefined' && player_aaaa && player_aaaa.url) {
                          return String(player_aaaa.url);
                        }
                      } catch (e) {}
                      return null;
                    }
                    """);
            if (url instanceof String s && !s.isBlank()) {
                media.add(resolveAgainstPage(page, s));
            }
        } catch (RuntimeException e) {
            log.debug("player_aaaa evaluate skipped: {}", e.getMessage());
        }
    }

    private static String resolveAgainstPage(Page page, String candidate) {
        try {
            String pageUrl = page.url();
            if (pageUrl == null || pageUrl.isBlank()) {
                return candidate;
            }
            return URI.create(pageUrl).resolve(candidate).toString();
        } catch (RuntimeException e) {
            return candidate;
        }
    }

    private static Map<String, String> sanitizeHeaders(Map<String, String> extraHeaders) {
        if (extraHeaders == null || extraHeaders.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        extraHeaders.forEach((key, value) -> {
            if (key == null || value == null || key.isBlank()) {
                return;
            }
            if ("host".equalsIgnoreCase(key) || "content-length".equalsIgnoreCase(key)) {
                return;
            }
            headers.put(key, value);
        });
        return headers;
    }

    private void ensureStarted() {
        if (started) {
            return;
        }
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
            try {
                Map<String, String> env = new HashMap<>(System.getenv());
                env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
                playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(settings.headless()));
                available = true;
                log.info("Playwright Chromium launched (headless={})", settings.headless());
            } catch (RuntimeException e) {
                available = false;
                log.warn("Playwright Chromium unavailable, WebView rules will fall back to OkHttp: {}", e.getMessage());
                closeQuietly();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            closeQuietly();
            available = false;
        }
    }

    private void closeQuietly() {
        if (browser != null) {
            try {
                browser.close();
            } catch (RuntimeException ignored) {
                // already closed
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (RuntimeException ignored) {
                // already closed
            }
            playwright = null;
        }
    }
}
