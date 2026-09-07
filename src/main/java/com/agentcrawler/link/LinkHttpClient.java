package com.agentcrawler.link;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class LinkHttpClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AgentCrawlerLinkInspector/1.0)";
    private static final int MAX_HTML_BYTES = 64 * 1024;

    private final OkHttpClient client;

    public LinkHttpClient(int timeoutSeconds) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public ProbeResult probe(String url) {
        return fetchHtml(url).toProbe();
    }

    public HtmlFetch fetchHtml(String url) {
        Request get = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        try (Response response = client.newCall(get).execute()) {
            String html = readLimited(response.body());
            return new HtmlFetch(
                    response.code(),
                    response.request().url().toString(),
                    header(response, "Content-Type"),
                    response.isSuccessful(),
                    html,
                    null
            );
        } catch (IOException ex) {
            return new HtmlFetch(0, url, "", false, "", ex.getMessage());
        }
    }

    private static String header(Response response, String name) {
        String value = response.header(name);
        return value == null ? "" : value;
    }

    private static String readLimited(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        byte[] bytes = body.bytes();
        int length = Math.min(bytes.length, MAX_HTML_BYTES);
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }

    public record ProbeResult(int status, String finalUrl, String contentType, boolean successful) {
        public boolean looksDead() {
            return status == 404 || status == 410 || status == 451;
        }

        public boolean looksHtml() {
            return contentType != null && contentType.toLowerCase().contains("html");
        }

        public boolean looksVideo() {
            if (contentType == null) {
                return false;
            }
            String lower = contentType.toLowerCase();
            return lower.startsWith("video/")
                    || lower.contains("mpegurl")
                    || lower.contains("application/vnd.apple.mpegurl");
        }
    }

    public record HtmlFetch(
            int status,
            String finalUrl,
            String contentType,
            boolean successful,
            String html,
            String error
    ) {
        ProbeResult toProbe() {
            return new ProbeResult(status, finalUrl, contentType, successful);
        }
    }
}
