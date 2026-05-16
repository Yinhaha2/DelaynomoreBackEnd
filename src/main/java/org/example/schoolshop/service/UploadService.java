package org.example.schoolshop.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface UploadService {

    Map<String, String> uploadImage(long userId, MultipartFile file);

    Map<String, String> uploadMaterial(long userId, MultipartFile file);
}
