package com.agentcrawler.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatAttachment(
        String type,
        @JsonProperty("image_id") String imageId,
        @JsonProperty("image_url") String imageUrl
) {
    public boolean isImage() {
        return type == null || type.isBlank() || "image".equalsIgnoreCase(type);
    }
}
