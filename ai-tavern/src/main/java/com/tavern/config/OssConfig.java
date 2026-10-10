package com.tavern.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "storage.oss")
public class OssConfig {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucket;
    private String publicBaseUrl;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "storage.oss", name = "access-key-id")
    public OSS ossClient() {
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            log.warn("OSS AccessKey 未配置，图片上传将使用占位地址");
            return null;
        }
        log.info("阿里云 OSS 已启用, endpoint={}, bucket={}", endpoint, bucket);
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
