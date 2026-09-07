package com.agentcrawler.vision;

import com.agentcrawler.config.AppProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnExpression("'${agent.llm.api-key:}'.length() > 0")
public class DeepSeekVisionClient {

    private static final String ANALYSIS_PROMPT = """
            你是动漫图像识别专家。请分析图片并识别其出处与关键实体。
            必须严格只输出 JSON，不要 Markdown，不要解释，字段如下：
            {
              "work_title": "作品名（含季/篇名，如 鬼灭之刃 游郭篇）",
              "characters": ["角色1", "角色2"],
              "episode_hint": "集数/时间点线索（如 第6集 或 未知）",
              "search_scene": "场景/名场面简述",
              "visual_features": ["视觉特征1", "视觉特征2"],
              "confidence": 0.0
            }
            confidence 为 0~1 的置信度。无法判断时 work_title 留空且 confidence 低于 0.5。
            """;

    private final AppProperties properties;
    private final ImageUploadService imageUploadService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekVisionClient(
            AppProperties properties,
            ImageUploadService imageUploadService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.imageUploadService = imageUploadService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(properties.llm().baseUrl()))
                .defaultHeader("Authorization", "Bearer " + properties.llm().apiKey())
                .build();
    }

    public AnimeVisionAnalysisResult analyze(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "imageUrl 不能为空");
        }

        Map<String, Object> imageContent;
        try {
            imageContent = buildImageContent(imageUrl.trim());
        } catch (IOException ex) {
            throw new AppException(ErrorCode.VISION_FAILED, "读取图片失败: " + ex.getMessage());
        }
        Map<String, Object> requestBody = Map.of(
                "model", properties.vision().model(),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", ANALYSIS_PROMPT),
                                imageContent
                        )
                ))
        );

        try {
            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            String content = extractAssistantContent(responseBody);
            return VisionJsonParser.parse(objectMapper, content);
        } catch (RestClientException | IOException ex) {
            throw new AppException(ErrorCode.VISION_FAILED, "视觉识别失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> buildImageContent(String imageUrl) throws IOException {
        if (imageUploadService.isInternalImageUrl(imageUrl)) {
            String imageId = imageUploadService.imageIdFromInternalUrl(imageUrl);
            ImageUploadStore.StoredImage stored = imageUploadService.require(imageId);
            byte[] bytes = Files.readAllBytes(Path.of(stored.filePath()));
            String dataUrl = "data:" + stored.mimeType() + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
            return imageUrlBlock(dataUrl);
        }
        return imageUrlBlock(imageUrl);
    }

    private Map<String, Object> imageUrlBlock(String url) {
        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", url);
        imageUrl.put("detail", properties.vision().detail());
        return Map.of("type", "image_url", "image_url", imageUrl);
    }

    private static String extractAssistantContent(String responseBody) throws IOException {
        JsonNode root = new ObjectMapper().readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IOException("视觉模型返回为空");
        }
        return content.asText();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.deepseek.com/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
