package com.agentcrawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AppProperties(
        String handler,
        int textChunkMaxChars,
        Crawler crawler,
        Llm llm,
        Vision vision,
        Upload upload,
        Link link
) {
    public Crawler.WebView webviewSettings() {
        Crawler configured = crawler;
        if (configured == null || configured.webview() == null) {
            return new Crawler.WebView(true, true, 25, 8);
        }
        return configured.webview();
    }

    public record Crawler(
            int maxSearchResults,
            int maxEpisodesPerRoad,
            int requestTimeoutSeconds,
            WebView webview,
            Fallback fallback
    ) {
        public Crawler {
            if (webview == null) {
                webview = new WebView(true, true, 25, 8);
            }
            if (fallback == null) {
                fallback = Fallback.defaults();
            }
        }

        public record WebView(
                boolean enabled,
                boolean headless,
                int navigationTimeoutSeconds,
                int settleTimeoutSeconds
        ) {}

        public record Fallback(boolean enabled, Yhdm yhdm, Silisili silisili) {
            public Fallback {
                if (yhdm == null) {
                    yhdm = new Yhdm(true, "http://www.iyinghua.io", "https://tup.iyinghua.com/?vid=%s");
                }
                if (silisili == null) {
                    silisili = new Silisili(true, "https://www.silisili.link", "silisili=on");
                }
            }

            public static Fallback defaults() {
                return new Fallback(true, null, null);
            }

            public static Fallback disabled() {
                return new Fallback(
                        false,
                        new Yhdm(false, "http://www.iyinghua.io", "https://tup.iyinghua.com/?vid=%s"),
                        new Silisili(false, "https://www.silisili.link", "silisili=on")
                );
            }

            public record Yhdm(boolean enabled, String baseUrl, String playUrlTemplate) {}

            public record Silisili(boolean enabled, String baseUrl, String cookie) {}
        }
    }

    public record Llm(
            String apiKey,
            String baseUrl,
            String model,
            int memoryMaxMessages
    ) {}

    public record Vision(
            String model,
            String detail,
            double minConfidenceToLock
    ) {}

    public record Upload(
            String storageDir,
            String publicBaseUrl,
            long maxSizeBytes
    ) {}

    public record Link(
            int timeoutSeconds,
            int maxLinksPerMessage,
            double minConfidenceToLock,
            boolean allowPrivateHosts
    ) {}
}
