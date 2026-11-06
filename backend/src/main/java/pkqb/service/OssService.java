package pkqb.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 阿里云OSS服务
 * 系统唯一的对象存储：文件、头像、试卷HTML走持久前缀（无生命周期限制），
 * 题目解析图片走 question-image/ 前缀（1天自动清理，保存时转存持久前缀）
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
     * 注意：SetBucketLifecycle 为全量替换语义，必须把桶上已有的其他规则一并带上，
     * 否则会静默清除他人配置的规则
     */
    @PostConstruct
    public void initLifecycle() {
        try {
            String ruleId = "question-image-expiration";
            String prefix = "question-image/";

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

            // 保留桶上已有的其他规则，仅追加自己的规则
            List<LifecycleRule> rulesToSet = new ArrayList<>(existingRules != null ? existingRules : List.of());
            rulesToSet.add(rule);

            SetBucketLifecycleRequest request = new SetBucketLifecycleRequest(bucketName);
            request.setLifecycleRules(rulesToSet);
            ossClient.setBucketLifecycle(request);
            log.info("[OSS] 已设置生命周期规则：{}/ 下文件 {} 天后自动删除（保留已有规则 {} 条）",
                    prefix, EXPIRATION_DAYS, rulesToSet.size() - 1);
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
     * 以流方式上传文件到OSS（知识库文件、试卷HTML等持久内容）
     */
    public void upload(String objectKey, InputStream inputStream, String contentType, long size) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (contentType != null) {
                metadata.setContentType(contentType);
            }
            metadata.setContentLength(size);
            ossClient.putObject(bucketName, objectKey, inputStream, metadata);
            log.debug("[OSS] 上传成功, key: {}", objectKey);
        } catch (Exception e) {
            throw new RuntimeException("上传文件到OSS失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件内容为字节数组
     */
    public byte[] getFile(String objectKey) {
        return download(objectKey).getData();
    }

    /**
     * 删除OSS文件（失败仅告警，不阻断主流程）
     */
    public void remove(String objectKey) {
        delete(objectKey);
    }

    /**
     * 生成临时上传地址（预签名 PUT）
     * 注意：OSS 签名包含 Content-Type，客户端 PUT 时必须携带与签名一致的 Content-Type 头，
     * 否则会报 SignatureDoesNotMatch
     *
     * @param objectKey     对象Key（路径）
     * @param contentType   客户端上传时携带的 Content-Type，可为 null（客户端则不能带该头）
     * @param expirySeconds 有效期（秒）
     * @return 预签名上传URL
     */
    public String generatePresignedPutUrl(String objectKey, String contentType, int expirySeconds) {
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(bucketName, objectKey, HttpMethod.PUT);
        request.setExpiration(new Date(System.currentTimeMillis() + expirySeconds * 1000L));
        if (contentType != null && !contentType.isEmpty()) {
            request.setContentType(contentType);
        }
        return ossClient.generatePresignedUrl(request).toString();
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
