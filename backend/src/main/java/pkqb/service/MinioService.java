package pkqb.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.presigned-url-expiry}")
    private int presignedUrlExpiry;

    /**
     * 确保 bucket 存在
     */
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.info("创建 bucket 失败: e={}" + e.getMessage());
        }
    }

    /**
     * 上传文件到 MinIO
     *
     * @param objectKey   对象路径
     * @param inputStream 文件流
     * @param contentType 文件类型
     * @param size        文件大小
     */
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
            log.info("上传文件失败: {}" + e.getMessage());
        }
    }

    /**
     * 删除 MinIO 对象
     */
    public void remove(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            log.info("删除文件失败: {}" + e.getMessage());
        }
    }

    /**
     * 获取预签名 URL（用于前端下载）
     */
    public String getPresignedGetUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(presignedUrlExpiry, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.info("获取预签名URL失败: {}" + e.getMessage());
            return null;
        }
    }

    /**
     * 获取预签名上传 URL（用于前端直传）
     */
    public String getPresignedPutUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(presignedUrlExpiry, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("获取预签名上传URL失败: " + e.getMessage());
        }
    }
}
