package org.example.schoolshop.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.schoolshop.config.SchoolShopProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final SchoolShopProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public boolean isConfigured() {
        return properties.isAiConfigured();
    }

    public String chat(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            return null;
        }
        SchoolShopProperties.Ai ai = properties.getAi();
        String url = ai.getBaseUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        url = url + "/chat/completions";
        Map<String, Object> body = Map.of(
                "model", ai.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "temperature", 0.7
        );
        try {
            String response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + ai.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            return node.path("choices").path(0).path("message").path("content").asText(null);
        } catch (Exception e) {
            log.error("LLM chat failed", e);
            return null;
        }
    }
}
