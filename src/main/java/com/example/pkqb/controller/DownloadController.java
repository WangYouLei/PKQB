package com.example.pkqb.controller;

import com.example.pkqb.config.AppConfig;
import com.example.pkqb.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 文件下载 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DownloadController {

    private final AppConfig appConfig;

    /**
     * 下载生成的HTML文件
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String fileName,
            @RequestParam(value = "downloadPath", required = false, defaultValue = "") String downloadPath) {
        try {
            log.info("收到下载请求，文件名: {}, 下载路径: {}", fileName, downloadPath);

            // 获取生成目录
            String generatedPath = appConfig.getGeneratedPath();
            
            // 如果用户指定了下载路径，使用用户指定的路径
            String filePath;
            if (downloadPath != null && !downloadPath.isEmpty()) {
                filePath = downloadPath + "/" + fileName;
            } else {
                filePath = Paths.get(generatedPath, fileName).toString();
            }

            // 检查文件是否存在
            java.nio.file.Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.error("文件不存在: {}", filePath);
                return ResponseEntity.status(404)
                        .body(("文件不存在: " + fileName).getBytes());
            }

            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(path);

            // 确定文件名编码
            String encodedFileName = new String(fileName.getBytes("UTF-8"), "UTF-8");

            // 设置响应头
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"")
                    .contentType(MediaType.TEXT_HTML)
                    .body(fileContent);

        } catch (IOException e) {
            log.error("下载文件失败", e);
            return ResponseEntity.internalServerError()
                    .body(("下载失败: " + e.getMessage()).getBytes());
        }
    }
}
