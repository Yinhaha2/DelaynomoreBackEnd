package com.agentcrawler.crawler.reliability;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 先用 HEAD，或只读 m3u8 开头几行，确认链接还在。403 / 超时不当成失效，避免热链拦截被误判。
 */
@Component
public class PlaybackLinkProbe implements PlaybackProbe {
    private static final int PREFIX_BYTES = 512;

    private final OkHttpClient client;

    public PlaybackLinkProbe() {
        this(new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .followRedirects(true)
                .build());
    }

    PlaybackLinkProbe(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public Verdict probe(CrawlResourceResult result) {
        String url = firstPlayableUrl(result);
        if (url == null) {
            return Verdict.UNKNOWN;
        }
        boolean playlist = url.toLowerCase().contains(".m3u8");
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", "agent-crawler/1.0");
        if (playlist) {
            builder.get().header("Range", "bytes=0-" + (PREFIX_BYTES - 1));
        } else {
            builder.head();
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            return classify(response.code(), playlist, playlist ? prefix(response) : "");
        } catch (Exception ex) {
            return Verdict.UNKNOWN;
        }
    }

    static Verdict classify(int status, boolean playlist, String bodyPrefix) {
        if (status == 404 || status == 410) {
            return Verdict.STALE;
        }
        if (playlist) {
            if (status != 200 && status != 206) {
                return Verdict.UNKNOWN;
            }
            String trimmed = bodyPrefix == null ? "" : bodyPrefix.stripLeading();
            if (trimmed.startsWith("#EXTM3U") || trimmed.startsWith("#EXT")) {
                return Verdict.FRESH;
            }
            return trimmed.isEmpty() ? Verdict.UNKNOWN : Verdict.STALE;
        }
        if (status >= 200 && status < 400) {
            return Verdict.FRESH;
        }
        return Verdict.UNKNOWN;
    }

    private static String prefix(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            byte[] buf = new byte[PREFIX_BYTES];
            int read = response.body().byteStream().read(buf);
            if (read <= 0) {
                return "";
            }
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String firstPlayableUrl(CrawlResourceResult result) {
        if (result == null || result.videos() == null) {
            return null;
        }
        for (CrawlResourceResult.VideoResource video : result.videos()) {
            if (video == null || video.url() == null) {
                continue;
            }
            String url = video.url().trim();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
        }
        return null;
    }
}
