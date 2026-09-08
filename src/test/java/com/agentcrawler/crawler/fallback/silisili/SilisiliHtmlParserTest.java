package com.agentcrawler.crawler.fallback.silisili;

import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilisiliHtmlParserTest {

    @Test
    void parsesSearchAndSkipsDownloadRoad() {
        List<SearchItem> items = SilisiliHtmlParser.parseSearch(
                """
                <article class="post-list">
                  <div class="search-image">
                    <a href="/vod/1/" title="葬送的芙莉莲">
                      <img srcset="https://cdn.example/cover.jpg 1x">
                    </a>
                  </div>
                  <div class="entry-summary">简介</div>
                </article>
                """,
                "https://sili.test/"
        );
        assertEquals(1, items.size());
        assertEquals("葬送的芙莉莲", items.get(0).name());

        List<Road> roads = SilisiliHtmlParser.parseChapters(
                """
                <div class="play-pannel-box">
                  <div class="widget-title">下载</div>
                  <ul><li><a href="/dl/1">下载1</a></li></ul>
                </div>
                <div class="play-pannel-box">
                  <div class="widget-title">播放列表</div>
                  <ul><li><a href="/vodplay/1">第1集</a></li></ul>
                </div>
                """,
                "https://sili.test/"
        );
        assertEquals(1, roads.size());
        assertEquals("第1集", roads.get(0).episodeNames().get(0));
    }

    @Test
    void decryptsPlayerPayload() throws Exception {
        String prefix = "SakuraKey";
        String digest = DigestUtils.md5DigestAsHex(prefix.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(digest.substring(16).getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(digest.substring(0, 16).getBytes(StandardCharsets.UTF_8))
        );
        String json = "{\"url\":\"https://cdn.example/a.m3u8\",\"fenjihtml\":\"<ul></ul>\"}";
        String body = prefix + Base64.getEncoder().encodeToString(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        var decoded = SilisiliPlayerDecoder.decode(body).orElseThrow();
        assertEquals("https://cdn.example/a.m3u8", decoded.url());
        assertTrue(decoded.fenjiHtml().contains("ul"));
    }
}
