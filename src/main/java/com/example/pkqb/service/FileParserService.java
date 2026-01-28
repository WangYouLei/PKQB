package com.example.pkqb.service;

import com.example.pkqb.config.AppConfig;
import com.example.pkqb.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件解析服务（统一入口）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileParserService {

    private final WordParserService wordParserService;
    private final PdfParserService pdfParserService;
    private final AppConfig appConfig;

    /**
     * 根据文件类型解析文件
     *
     * @param fileType 文件类型：word/pdf
     * @param filePath 文件路径
     * @return 解析后的文本
     */
    public String parseFile(String fileType, String filePath) {
        log.info("开始解析文件，类型: {}, 路径: {}", fileType, filePath);

        try {
            String text;

            switch (fileType.toLowerCase()) {
                case "word":
                    text = wordParserService.parse(filePath);
                    break;
                case "pdf":
                    text = pdfParserService.parse(filePath);
                    break;
                default:
                    throw new BusinessException("不支持的文件类型: " + fileType);
            }

            log.info("文件解析完成，文本长度: {}", text.length());
            return text;

        } catch (Exception e) {
            log.error("文件解析失败", e);
            throw new BusinessException("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 保存上传的文件
     *
     * @param fileName 原始文件名
     * @param fileBytes  文件字节数组
     * @return 保存后的文件路径
     */
    public String saveUploadedFile(String fileName, byte[] fileBytes) {
        try {
            // 确保上传目录存在
            String uploadPath = appConfig.getUploadPath();
            Files.createDirectories(Paths.get(uploadPath));

            // 生成唯一文件名
            String fileExtension = getFileExtension(fileName);
            String uniqueFileName = UUID.randomUUID() + "." + fileExtension;
            String filePath = Paths.get(uploadPath, uniqueFileName).toString();

            // 保存文件
            Files.write(Paths.get(filePath), fileBytes);

            log.info("文件保存成功: {}", filePath);

            return filePath;

        } catch (Exception e) {
            log.error("保存上传文件失败", e);
            throw new BusinessException("保存上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "unknown";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
}
