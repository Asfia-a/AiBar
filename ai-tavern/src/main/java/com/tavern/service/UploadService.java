package com.tavern.service;

import com.aliyun.oss.OSS;
import com.tavern.common.BusinessException;
import com.tavern.config.OssConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final ObjectProvider<OSS> ossClientProvider;
    private final OssConfig ossConfig;

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        try {
            return uploadBytes(file.getBytes(), ext.isBlank() ? ".bin" : ext);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("上传失败: " + e.getMessage());
        }
    }

    public String uploadBytes(byte[] bytes, String ext) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("文件为空");
        }
        if (ext == null || ext.isBlank()) {
            ext = ".png";
        }
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        String datePath = java.time.LocalDate.now().toString().replace("-", "/");
        String key = "ai-tavern/" + datePath + "/" + UUID.randomUUID() + ext;

        OSS ossClient = ossClientProvider.getIfAvailable();
        if (ossClient == null) {
            throw new BusinessException("OSS 未配置或初始化失败，无法上传");
        }
        try {
            ossClient.putObject(ossConfig.getBucket(), key, new java.io.ByteArrayInputStream(bytes));
            String base = ossConfig.getPublicBaseUrl();
            if (base == null || base.isBlank()) {
                base = "https://" + ossConfig.getBucket() + "." + ossConfig.getEndpoint();
            }
            if (base.endsWith("/")) {
                return base + key;
            }
            return base + "/" + key;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("上传失败: " + e.getMessage());
        }
    }
}
