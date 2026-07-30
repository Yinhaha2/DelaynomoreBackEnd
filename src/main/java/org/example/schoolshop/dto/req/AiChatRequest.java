package org.example.schoolshop.dto.req;

import lombok.Data;

import java.util.Map;

@Data
public class AiChatRequest {
    private String message;
    private Map<String, Object> context;
}
