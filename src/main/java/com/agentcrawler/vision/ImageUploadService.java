package com.agentcrawler.vision;

import com.agentcrawler.config.AppProperties;
import com.agentcrawler.core.AppException;
import com.agentcrawler.core.ErrorCode;
import com.agentcrawler.core.IdGenerator;
import com.agentcrawler.model.ImageUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Service
public class ImageUploadService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private final AppProperties properties;
    private final ImageUploadStore store;

    public ImageUploadService(AppProperties properties, ImageUploadStore store) {
        this.properties = properties;
        this.store = store;
    }

    public ImageUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > properties.upload().maxSizeBytes()) {
            throw new AppException(ErrorCode.UPLOAD_FAILED, "图片大小超过限制");
        }

        String mimeType = detectMimeType(file);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new AppException(ErrorCode.UPLOAD_FAILED, "仅支持 JPEG/PNG/GIF/WebP 图片");
        }

        String imageId = IdGenerator.imageId();
        Path storageDir = Path.of(properties.upload().storageDir());
        try {
            Files.createDirectories(storageDir);
            String extension = extensionForMime(mimeType);
            Path target = storageDir.resolve(imageId + extension);
            file.transferTo(target);

            store.save(new ImageUploadStore.StoredImage(imageId, target.toString(), mimeType, file.getSize()));
            return new ImageUploadResponse(
                    imageId,
                    buildPublicUrl(imageId),
                    mimeType,
                    file.getSize()
            );
        } catch (IOException ex) {
            throw new AppException(ErrorCode.UPLOAD_FAILED, "图片保存失败: " + ex.getMessage());
        }
    }

    public ImageUploadStore.StoredImage require(String imageId) {
        return store.find(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "图片不存在: " + imageId));
    }

    public String buildPublicUrl(String imageId) {
        String base = properties.upload().publicBaseUrl().replaceAll("/+$", "");
        return base + "/api/v1/files/images/" + imageId;
    }

    public boolean isInternalImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        String prefix = properties.upload().publicBaseUrl().replaceAll("/+$", "")
                + "/api/v1/files/images/";
        return imageUrl.startsWith(prefix);
    }

    public String imageIdFromInternalUrl(String imageUrl) {
        String prefix = properties.upload().publicBaseUrl().replaceAll("/+$", "")
                + "/api/v1/files/images/";
        if (!imageUrl.startsWith(prefix)) {
            return null;
        }
        return imageUrl.substring(prefix.length()).split("[?#]")[0];
    }

    private static String detectMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            return contentType.toLowerCase(Locale.ROOT);
        }
        String name = file.getOriginalFilename();
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static String extensionForMime(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
