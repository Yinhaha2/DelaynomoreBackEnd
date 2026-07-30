package org.example.schoolshop.integration.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.schoolshop.config.SchoolShopProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;

@Slf4j
@Service
public class OssStorageService {

    private final SchoolShopProperties properties;
    private volatile OSS client;

    public OssStorageService(SchoolShopProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.isOssConfigured();
    }

    public String uploadPublic(String key, MultipartFile file) {
        upload(properties.getOss().getPublicBucket(), key, file);
        String base = properties.getOss().getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + key;
    }

    public void uploadPrivate(String key, MultipartFile file) {
        upload(properties.getOss().getPrivateBucket(), key, file);
    }

    public String presignedPrivateUrl(String key, int expireSeconds) {
        OSS oss = client();
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                properties.getOss().getPrivateBucket(), key);
        request.setExpiration(expiration);
        URL url = oss.generatePresignedUrl(request);
        return url.toString();
    }

    private void upload(String bucket, String key, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            if (file.getContentType() != null) {
                meta.setContentType(file.getContentType());
            }
            client().putObject(bucket, key, in, meta);
        } catch (Exception e) {
            log.error("OSS upload failed key={}", key, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private OSS client() {
        if (!isConfigured()) {
            throw new IllegalStateException("OSS 未配置");
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    SchoolShopProperties.Oss oss = properties.getOss();
                    client = new OSSClientBuilder().build(
                            oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
                }
            }
        }
        return client;
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
