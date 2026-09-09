package com.agentcrawler.agent.langchain;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.model.CrawlResourceResult.VideoResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceRouteGrouperTest {

    @Test
    void groupsSiliSiliCdnsByFamilyAndDedupsEpisodes() {
        CrawlResourceResult result = new CrawlResourceResult(
                "鬼灭之刃",
                "SiliSili",
                "SiliSili",
                List.of(
                        video("第01集", "https://svipsvip.ffzy-online5.com/20240513/27191/index.m3u8"),
                        video("第02集", "https://svipsvip.ffzy-online5.com/20240520/27465/index.m3u8"),
                        video("第01集", "https://123456.silisililove.com/m3u8.php?aaa.m3u8"),
                        video("第02集", "https://123456.silisililove.com/m3u8.php?bbb.m3u8"),
                        video("第01集", "https://vip.ffzy-play3.com/20230410/10768/index.m3u8"),
                        video("第01集", "https://silivideo.example.r2.cloudflarestorage.com/G/%E9%AC%BC%E7%81%AD%E4%B9%8B%E5%88%83_1.mp4?X-Amz-Signature=abc"),
                        video("第05集", "https://silivideo.example.r2.cloudflarestorage.com/G/ep5.mp4?X-Amz-Signature=def"),
                        video("第02集", "https://silivideo.example.r2.cloudflarestorage.com/G/ep2.mp4?X-Amz-Signature=ghi")
                ),
                List.of(),
                List.of()
        );

        ResourceRouteGrouper.GroupedResources grouped = ResourceRouteGrouper.group(result);

        assertThat(grouped.uniqueEpisodeCount()).isEqualTo(3);
        assertThat(grouped.latestEpisode()).isEqualTo("第05集");
        assertThat(grouped.routes()).extracting(ResourceRouteGrouper.Route::name)
                .containsExactly("高速直链 (R2)", "非凡云播 (m3u8)", "Sili 专属源");
        assertThat(grouped.routes().get(0).recommended()).isTrue();
        assertThat(grouped.routes().get(0).kind()).isEqualTo("mp4");
        assertThat(grouped.routes().get(0).episodes()).extracting(ResourceRouteGrouper.Episode::title)
                .containsExactly("第01集", "第02集", "第05集");
        assertThat(grouped.routes().stream().filter(r -> r.name().equals("非凡云播 (m3u8)")).findFirst())
                .get()
                .extracting(route -> route.episodes().size())
                .isEqualTo(2);
    }

    @Test
    void groupsByPluginRoadNameWhenMultipleRoadsExist() {
        CrawlResourceResult result = new CrawlResourceResult(
                "葬送的芙莉莲",
                "DM84",
                "DM84",
                List.of(
                        new VideoResource("第1集", "https://cdn.example.com/a/index.m3u8", "https://site/play/1", "线路1"),
                        new VideoResource("第2集", "https://cdn.example.com/b/index.m3u8", "https://site/play/2", "线路1"),
                        new VideoResource("第1集", "https://backup.example.com/a/index.m3u8", "https://site/play/1", "线路2")
                ),
                List.of(),
                List.of()
        );

        ResourceRouteGrouper.GroupedResources grouped = ResourceRouteGrouper.group(result);

        assertThat(grouped.routes()).extracting(ResourceRouteGrouper.Route::name)
                .containsExactly("线路1", "线路2");
        assertThat(grouped.routes().get(0).episodes()).hasSize(2);
        assertThat(grouped.routes().get(1).episodes()).hasSize(1);
        assertThat(grouped.routes().get(0).recommended()).isTrue();
    }

    private static VideoResource video(String title, String url) {
        return new VideoResource(title, url, "https://example.com/play", null);
    }
}
