package com.agentcrawler.vision;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ImageUploadStore {

    public record StoredImage(String imageId, String filePath, String mimeType, long size) {}

    private final Map<String, StoredImage> images = new ConcurrentHashMap<>();

    public void save(StoredImage image) {
        images.put(image.imageId(), image);
    }

    public Optional<StoredImage> find(String imageId) {
        return Optional.ofNullable(images.get(imageId));
    }
}
