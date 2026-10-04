package com.agentcrawler.crawler.reliability;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackLinkProbeTest {

    @Test
    void playlistPrefixIsFreshMissingPlaylistIsStaleAndForbiddenStaysUnknown() throws Exception {
        assertEquals(PlaybackProbe.Verdict.FRESH, PlaybackLinkProbe.classify(206, true, "#EXTM3U\n#EXTINF:1,\n"));
        assertEquals(PlaybackProbe.Verdict.STALE, PlaybackLinkProbe.classify(404, false, ""));
        assertEquals(PlaybackProbe.Verdict.STALE, PlaybackLinkProbe.classify(200, true, "<html>gone</html>"));
        assertEquals(PlaybackProbe.Verdict.UNKNOWN, PlaybackLinkProbe.classify(403, false, ""));

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(206).setBody("#EXTM3U\n#EXTINF:1,\nseg.ts\n"));
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();

            PlaybackLinkProbe probe = new PlaybackLinkProbe();
            assertEquals(PlaybackProbe.Verdict.FRESH, probe.probe(sample(server.url("/a.m3u8").toString())));
            assertEquals(PlaybackProbe.Verdict.STALE, probe.probe(sample(server.url("/gone.m3u8").toString())));
        }
    }

    private static CrawlResourceResult sample(String url) {
        return new CrawlResourceResult(
                "芙莉莲",
                "DM84",
                "DM84",
                List.of(new CrawlResourceResult.VideoResource("第1集", url, "http://page", "线路1")),
                List.of(),
                List.of()
        );
    }
}
