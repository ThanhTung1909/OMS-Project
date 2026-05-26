package com.oms.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnBean(Cloudinary.class)
@Slf4j
public class UploadService {

    private final Cloudinary cloudinary;

    @Autowired
    public UploadService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadFile(MultipartFile file, String folderName) throws IOException {
        try {
            String publicId = UUID.randomUUID().toString();
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folderName,
                    "public_id", publicId
            ));
            return uploadResult.get("secure_url").toString();
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary", e);
            throw new IOException("Failed to upload file: " + e.getMessage());
        }
    }
}
