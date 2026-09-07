package com.agentcrawler.agent.langchain;

import com.agentcrawler.model.ChatAttachment;
import com.agentcrawler.vision.ImageUploadService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentMessageEnricherTest {

    @Test
    void enrichesMessageWithImageUrlFromImageId() {
        ImageUploadService uploadService = mock(ImageUploadService.class);
        when(uploadService.buildPublicUrl("img_abc")).thenReturn("http://localhost:8000/api/v1/files/images/img_abc");

        String enriched = AttachmentMessageEnricher.enrich(
                "这是哪一集？",
                List.of(new ChatAttachment("image", "img_abc", null)),
                uploadService
        );

        assertThat(enriched).contains("analyzeAnimeImage");
        assertThat(enriched).contains("http://localhost:8000/api/v1/files/images/img_abc");
        assertThat(enriched).contains("这是哪一集？");
    }

    @Test
    void returnsOriginalWhenNoAttachments() {
        ImageUploadService uploadService = mock(ImageUploadService.class);
        assertThat(AttachmentMessageEnricher.enrich("hello", List.of(), uploadService)).isEqualTo("hello");
    }
}
