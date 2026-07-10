package pkqb.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.LifecycleRule;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.SetBucketLifecycleRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

/**
 * 阿里云OSS服务
 * 用于存储题目解析相关的图片（供DashScope模型和前端访问）
 */
@Slf4j
@Service
public class OssService {

    private final OSS ossClient;
    private final String bucketName;
    private final String endpoint;

    /** 图片过期天数，超过后OSS自动清理 */
    private static final int EXPIRATION_DAYS = 1;

    public OssService(
            @Value("${aliyun-oss.endpoint}") String endpoint,
            @Value("${aliyun-oss.access-key-id}") String accessKeyId,
            @Value("${aliyun-oss.access-key-secret}") String accessKeySecret,
            @Value("${aliyun-oss.bucket-name}") String bucketName) {
        this.endpoint = endpoint;
        this.bucketName = bucketName;
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        log.info("[OSS] 初始化完成, endpoint: {}, bucket: {}", endpoint, bucketName);
    }

    /**
     * 初始化时设置 bucket 生命周期规则，自动清理过期图片
     */
    @PostConstruct
    public void initLifecycle() {
        try {
            String ruleId = "question-image-expiration";
            String prefix = "question-image/";

            // 检查是否已有相同规则，避免重复添加
            List<LifecycleRule> existingRules = ossClient.getBucketLifecycle(bucketName);
            if (existingRules != null) {
                for (LifecycleRule rule : existingRules) {
                    if (ruleId.equals(rule.getId())) {
                        log.info("[OSS] 生命周期规则已存在，跳过创建");
                        return;
                    }
                }
            }

            LifecycleRule rule = new LifecycleRule();
            rule.setId(ruleId);
            rule.setPrefix(prefix);
            rule.setStatus(LifecycleRule.RuleStatus.Enabled);
            rule.setExpirationDays(EXPIRATION_DAYS);

            SetBucketLifecycleRequest request = new SetBucketLifecycleRequest(bucketName);
            request.setLifecycleRules(List.of(rule));
            ossClient.setBucketLifecycle(request);
            log.info("[OSS] 已设置生命周期规则：{}/ 下文件 {} 天后自动删除", prefix, EXPIRATION_DAYS);
        } catch (Exception e) {
            log.warn("[OSS] 设置生命周期规则失败（不影响功能）: {}", e.getMessage());
        }
    }

    /**
     * 上传字节数组到OSS
     *
     * @param objectKey  对象Key（路径）
     * @param data       字节数据
     * @param contentType 内容类型
     * @return 公网可访问的URL
     */
    public String upload(String objectKey, byte[] data, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(data.length);
        ossClient.putObject(bucketName, objectKey, new ByteArrayInputStream(data), metadata);
        String url = getPublicUrl(objectKey);
        log.debug("[OSS] 上传成功, key: {}, url: {}", objectKey, url);
        return url;
    }

    /**
     * 获取对象的永久URL（通过OSS公共读bucket的直链格式）
     * 格式：https://{bucketName}.{endpoint}/{objectKey}
     */
    public String getPublicUrl(String objectKey) {
        String ep = endpoint.replaceFirst("^https?://", "");
        return "https://" + bucketName + "." + ep + "/" + objectKey;
    }

    /**
     * 生成预签名URL（带过期时间）
     *
     * @param objectKey 对象Key
     * @param expireMinutes 过期时间（分钟）
     * @return 预签名URL
     */
    public String generatePresignedUrl(String objectKey, int expireMinutes) {
        Date expiration = new Date(System.currentTimeMillis() + expireMinutes * 60 * 1000L);
        return ossClient.generatePresignedUrl(bucketName, objectKey, expiration).toString();
    }

    /**
     * 从URL中提取objectKey
     * URL格式：https://{bucketName}.{endpoint}/{objectKey}
     */
    public String extractObjectKeyFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            String ep = endpoint.replaceFirst("^https?://", "");
            String prefix = "https://" + bucketName + "." + ep + "/";
            if (url.startsWith(prefix)) {
                return url.substring(prefix.length());
            }
            // 兼容可能的其他URL格式
            if (url.contains(bucketName + "." + ep + "/")) {
                int idx = url.indexOf(bucketName + "." + ep + "/");
                return url.substring(idx + (bucketName + "." + ep + "/").length());
            }
        } catch (Exception e) {
            log.warn("[OSS] 从URL提取objectKey失败: {}", url);
        }
        return null;
    }

    /**
     * 判断URL是否属于当前OSS Bucket
     */
    public boolean isOssUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String ep = endpoint.replaceFirst("^https?://", "");
        return url.contains(bucketName + "." + ep + "/");
    }

    /**
     * 下载OSS文件，返回字节数组和ContentType
     */
    public OssFileData download(String objectKey) {
        try {
            OSSObject ossObject = ossClient.getObject(bucketName, objectKey);
            try (InputStream inputStream = ossObject.getObjectContent();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }
                String contentType = ossObject.getObjectMetadata().getContentType();
                return new OssFileData(outputStream.toByteArray(), contentType);
            }
        } catch (Exception e) {
            throw new RuntimeException("下载OSS文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除OSS文件
     */
    public void delete(String objectKey) {
        try {
            ossClient.deleteObject(bucketName, objectKey);
            log.debug("[OSS] 删除文件成功, key: {}", objectKey);
        } catch (Exception e) {
            log.warn("[OSS] 删除OSS文件失败（不影响主流程）, key: {}, error: {}", objectKey, e.getMessage());
        }
    }

    /**
     * OSS文件数据
     */
    public static class OssFileData {
        private final byte[] data;
        private final String contentType;

        public OssFileData(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        public byte[] getData() { return data; }
        public String getContentType() { return contentType; }
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("[OSS] 客户端已关闭");
        }
    }
}
