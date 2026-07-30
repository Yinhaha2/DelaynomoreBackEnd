package org.example.schoolshop.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.config.SchoolShopProperties;
import org.example.schoolshop.domain.UploadFile;
import org.example.schoolshop.integration.oss.OssStorageService;
import org.example.schoolshop.mapper.UploadFileMapper;
import org.example.schoolshop.service.UploadService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private static final long IMAGE_MAX = 5L * 1024 * 1024;
    private static final long MATERIAL_MAX = 20L * 1024 * 1024;

    private final UploadFileMapper uploadFileMapper;
    private final OssStorageService ossStorageService;
    private final SchoolShopProperties properties;

    @Override
    public Map<String, String> uploadImage(long userId, MultipartFile file) {
        validateImage(file);
        String key = "posts/" + java.time.LocalDate.now() + "/u" + userId + "_" + UUID.randomUUID() + ext(file);
        saveRecord(userId, key, file.getOriginalFilename(), ext(file).replace(".", ""), "image");
        String url;
        if (ossStorageService.isConfigured()) {
            url = ossStorageService.uploadPublic(key, file);
        } else {
            String base = properties.getOss().getPublicBaseUrl();
            if (!StringUtils.hasText(base)) {
                base = "https://your-bucket.mock.aliyuncs.com";
            }
            url = base.endsWith("/") ? base + key : base + "/" + key;
        }
        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return data;
    }

    @Override
    public Map<String, String> uploadMaterial(long userId, MultipartFile file) {
        String ext = ext(file).toLowerCase();
        if (!ext.equals(".pdf") && !ext.equals(".doc") && !ext.equals(".docx")) {
            throw BizException.badRequest("不支持的文件格式");
        }
        if (file.getSize() > MATERIAL_MAX) {
            throw BizException.badRequest("文件不能超过 20MB");
        }
        String key = "materials/" + java.time.LocalDate.now() + "/u" + userId + "_" + UUID.randomUUID() + ext;
        if (ossStorageService.isConfigured()) {
            ossStorageService.uploadPrivate(key, file);
        }
        saveRecord(userId, key, file.getOriginalFilename(), ext.replace(".", ""), "material");
        Map<String, String> data = new HashMap<>();
        data.put("fileKey", key);
        data.put("fileName", file.getOriginalFilename());
        data.put("fileType", ext.replace(".", ""));
        return data;
    }

    private void saveRecord(long userId, String key, String fileName, String fileType, String bizType) {
        UploadFile uf = new UploadFile();
        uf.setUserId(userId);
        uf.setFileKey(key);
        uf.setFileName(fileName != null ? fileName : "");
        uf.setFileType(fileType);
        uf.setBizType(bizType);
        uploadFileMapper.insert(uf);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("文件不能为空");
        }
        if (file.getSize() > IMAGE_MAX) {
            throw BizException.badRequest("图片不能超过 5MB");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png") && !name.endsWith(".webp")) {
            throw BizException.badRequest("不支持的文件格式");
        }
    }

    private String ext(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !name.contains(".")) {
            return ".bin";
        }
        return name.substring(name.lastIndexOf('.'));
    }
}
