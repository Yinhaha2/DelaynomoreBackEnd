package com.agentcrawler.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImageUploadResponse(
        @JsonProperty("image_id") String imageId,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("mime_type") String mimeType,
        long size
) {}
