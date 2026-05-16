package org.example.schoolshop.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.schoolshop.common.BizException;
import org.example.schoolshop.domain.UploadFile;
import org.example.schoolshop.mapper.UploadFileMapper;
import org.example.schoolshop.service.UploadService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final UploadFileMapper uploadFileMapper;

    @Override
    public Map<String, String> uploadImage(long userId, MultipartFile file) {
        validateImage(file);
        String key = "posts/" + java.time.LocalDate.now() + "/u" + userId + "_" + UUID.randomUUID() + ext(file);
        saveRecord(userId, key, file.getOriginalFilename(), ext(file).replace(".", ""), "image");
        // TODO: 上传 OSS 公共读
        Map<String, String> data = new HashMap<>();
        data.put("url", "https://your-bucket.mock.aliyuncs.com/" + key);
        return data;
    }

    @Override
    public Map<String, String> uploadMaterial(long userId, MultipartFile file) {
        String ext = ext(file).toLowerCase();
        if (!ext.equals(".pdf") && !ext.equals(".doc") && !ext.equals(".docx")) {
            throw BizException.badRequest("不支持的文件格式");
        }
        String key = "materials/" + java.time.LocalDate.now() + "/u" + userId + "_" + UUID.randomUUID() + ext;
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
