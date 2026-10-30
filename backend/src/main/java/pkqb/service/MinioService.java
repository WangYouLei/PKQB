package pkqb.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    // 桶存在性只需确认一次：缓存结果，避免每次上传/下载都多一次bucketExists网络往返
    private volatile boolean bucketChecked = false;

    public void ensureBucketExists() {
        if (bucketChecked) {
            return;
        }
        synchronized (this) {
            if (bucketChecked) {
                return;
            }
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucketName).build());
                if (!exists) {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder().bucket(bucketName).build());
                    log.info("Created bucket: {}", bucketName);
                }
                bucketChecked = true;
            } catch (Exception e) {
                throw new RuntimeException("创建Bucket失败: " + e.getMessage(), e);
            }
        }
    }

    public void upload(String objectKey, InputStream inputStream, String contentType, long size) {
        try {
            ensureBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("上传文件到MinIO失败: " + e.getMessage(), e);
        }
    }

    public void remove(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("删除MinIO文件失败: " + e.getMessage(), e);
        }
    }

    public byte[] getFile(String objectKey) {
        try {
            ensureBucketExists();
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = stream.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("获取文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成临时上传地址（预签名 PUT）
     * 只对指定 objectKey 生效且限时有效，避免把内网 endpoint 与桶的永久写权限暴露给前端
     *
     * @param objectKey     对象路径
     * @param expirySeconds 有效期（秒）
     * @return 预签名上传URL
     */
    public String getPresignedPutUrl(String objectKey, int expirySeconds) {
        try {
            ensureBucketExists();
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("生成临时上传地址失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件的访问URL
     */
    public String getFileUrl(String objectKey) {
        String baseEndpoint = endpoint.replaceAll("/+$", "");
        String encodedKey = URLEncoder.encode(objectKey, StandardCharsets.UTF_8)
                .replace("%2F", "/"); // 保留路径分隔符
        return baseEndpoint + "/" + bucketName + "/" + encodedKey;
    }
}
