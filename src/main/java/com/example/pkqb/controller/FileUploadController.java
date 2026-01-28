package com.example.pkqb.controller;

import com.example.pkqb.model.ContentRequest;
import com.example.pkqb.model.ContentResponse;
import com.example.pkqb.service.FileParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileParserService fileParserService;

    /**
     * 文件上传接口
     */
    @PostMapping("/upload")
    public ResponseEntity<ContentResponse<Map<String, String>>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String fileType) {

        try {
            log.info("收到文件上传请求，文件名: {}, 类型: {}", file.getOriginalFilename(), fileType);

            // 验证文件类型
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ContentResponse.error("文件不能为空"));
            }

            // 验证文件类型是否支持（只支持 Word 和 PDF）
            if (!fileType.equalsIgnoreCase("word") && !fileType.equalsIgnoreCase("pdf")) {
                return ResponseEntity.badRequest()
                        .body(ContentResponse.error("不支持的文件类型，仅支持 Word 和 PDF 文件"));
            }

            // 保存文件
            String fileName = file.getOriginalFilename();
            byte[] fileBytes = file.getBytes();
            String filePath = fileParserService.saveUploadedFile(fileName, fileBytes);

            // 返回文件信息
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("fileId", filePath);
            fileInfo.put("fileName", fileName);
            fileInfo.put("filePath", filePath);

            return ResponseEntity.ok(ContentResponse.success("文件上传成功", fileInfo));

        } catch (Exception e) {
            log.error("文件上传失败", e);
            return ResponseEntity.internalServerError()
                    .body(ContentResponse.error("文件上传失败: " + e.getMessage()));
        }
    }

    /**
     * 解析文件接口
     */
    @PostMapping("/parse")
    public ResponseEntity<ContentResponse<Map<String, String>>> parseFile(
            @RequestBody ContentRequest request) {

        try {
            log.info("收到文件解析请求，文件类型: {}, 文件路径: {}",
                    request.getFileType(), request.getFilePath());

            // 验证请求参数
            if (request.getFilePath() == null || request.getFilePath().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ContentResponse.error("文件路径不能为空"));
            }

            if (request.getFileType() == null || request.getFileType().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ContentResponse.error("文件类型不能为空"));
            }

            // 验证文件类型是否支持（只支持 Word 和 PDF）
            if (!request.getFileType().equalsIgnoreCase("word") && !request.getFileType().equalsIgnoreCase("pdf")) {
                return ResponseEntity.badRequest()
                        .body(ContentResponse.error("不支持的文件类型，仅支持 Word 和 PDF 文件"));
            }

            // 解析文件
            String text = fileParserService.parseFile(request.getFileType(), request.getFilePath());

            // 返回解析结果
            Map<String, String> result = new HashMap<>();
            result.put("text", text);

            return ResponseEntity.ok(ContentResponse.success("解析成功", result));

        } catch (Exception e) {
            log.error("文件解析失败", e);
            return ResponseEntity.internalServerError()
                    .body(ContentResponse.error("文件解析失败: " + e.getMessage()));
        }
    }
}