package com.agentcrawler;

import com.agentcrawler.crawler.model.CrawlResourceResult;
import com.agentcrawler.crawler.service.ResourceCrawlerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResourceCrawlerService crawlerService;

    @BeforeEach
    void setUp() {
        when(crawlerService.crawl(anyString(), anyString())).thenReturn(
                new CrawlResourceResult(
                        "葬送的芙莉莲",
                        "DM84",
                        "DM84",
                        List.of(new CrawlResourceResult.VideoResource(
                                "第1集",
                                "https://cdn.example.com/1.m3u8",
                                "https://example.com/play/1",
                                "线路1"
                        )),
                        List.of(new CrawlResourceResult.LinkResource(
                                "详情页",
                                "https://example.com/detail/1",
                                "搜索结果"
                        )),
                        List.of(new CrawlResourceResult.ImageResource(
                                "https://cdn.example.com/poster.jpg",
                                "葬送的芙莉莲"
                        ))
                )
        );
    }

    @Test
    void createConversation() throws Exception {
        mockMvc.perform(post("/api/v1/conversations").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id").exists())
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    void streamChatReturnsStructuredEvents() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"message":"帮我找《葬送的芙莉莲》的播放资源"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: chunk")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"text_delta\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"resource_bundle\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"video\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"link\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"image\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("第1集 =>"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")));
    }

    @Test
    void streamChatReturnsTitleWhenNeedTitle() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"message":"帮我找《葬送的芙莉莲》的播放资源","need_title":true}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"title\":")));
    }
}
