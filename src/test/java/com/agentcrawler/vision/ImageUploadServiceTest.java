package com.agentcrawler.vision;

import com.agentcrawler.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUploadServiceTest {

    @TempDir
    Path tempDir;

    private ImageUploadService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "langchain",
                512,
                new AppProperties.Crawler(3, 5, 5),
                new AppProperties.Llm("", "https://api.deepseek.com/v1", "deepseek-chat", 4),
                new AppProperties.Vision("deepseek-v4-flash-vision-exp", "original", 0.7),
                new AppProperties.Upload(tempDir.toString(), "http://localhost:8000", 33_554_432)
        );
        service = new ImageUploadService(properties, new ImageUploadStore());
    }

    @Test
    void uploadsPngAndBuildsPublicUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "meme.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );

        var response = service.upload(file);

        assertThat(response.imageId()).startsWith("img_");
        assertThat(response.imageUrl()).contains("/api/v1/files/images/");
        assertThat(service.require(response.imageId()).mimeType()).isEqualTo("image/png");
    }

    @Test
    void detectsInternalImageUrl() {
        String url = service.buildPublicUrl("img_test");
        assertThat(service.isInternalImageUrl(url)).isTrue();
        assertThat(service.imageIdFromInternalUrl(url)).isEqualTo("img_test");
    }
}
