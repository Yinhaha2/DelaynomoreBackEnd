package com.agentcrawler.crawler.fallback.yhdm;

import com.agentcrawler.crawler.model.Road;
import com.agentcrawler.crawler.model.SearchItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YhdmHtmlParserTest {

    @Test
    void parsesSearchAndPlayUrls() {
        List<SearchItem> items = YhdmHtmlParser.parseSearch(
                """
                <div class="lpic"><ul>
                  <li>
                    <img data-src="/cover.jpg">
                    <h2><a href="/show/1">葬送的芙莉莲</a></h2>
                    <p>简介</p>
                  </li>
                </ul></div>
                """,
                "http://yhdm.test/"
        );
        assertEquals(1, items.size());
        assertEquals("葬送的芙莉莲", items.get(0).name());
        assertTrue(items.get(0).src().contains("/show/1"));
        assertTrue(items.get(0).imageUrl().contains("/cover.jpg"));

        List<Road> roads = YhdmHtmlParser.parseChapters(
                """
                <div class="movurl"><ul>
                  <li><a href="/v/1.html">第1集</a></li>
                </ul></div>
                """,
                "http://yhdm.test/"
        );
        assertEquals("第1集", roads.get(0).episodeNames().get(0));

        List<String> play = YhdmHtmlParser.parsePlayUrls(
                "<div class=\"playbo\"><a onClick=\"changeplay('https://cdn.example/ep1.m3u8$mp4');\">线路</a></div>",
                "https://tup.example/?vid=%s"
        );
        assertTrue(play.contains("https://cdn.example/ep1.m3u8"));
    }
}
