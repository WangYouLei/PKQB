package com.example.pkqb.service;

import com.example.pkqb.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Word 文档解析服务
 */
@Slf4j
@Service
public class WordParserService {

    /**
     * 解析 Word 文档为文本
     *
     * @param filePath 文件路径
     * @return 解析后的文本
     */
    public String parse(String filePath) {
        File file = new File(filePath);
        String fileName = file.getName().toLowerCase();

        try {
            if (fileName.endsWith(".docx")) {
                return parseDocx(filePath);
            } else if (fileName.endsWith(".doc")) {
                return parseDoc(filePath);
            } else {
                throw new BusinessException("不支持的Word文件格式: " + fileName);
            }
        } catch (IOException e) {
            log.error("解析 Word 文档失败", e);
            throw new BusinessException("解析 Word 文档失败: " + e.getMessage());
        }
    }

    /**
     * 解析 .docx 格式
     */
    private String parseDocx(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder text = new StringBuilder();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    text.append(paragraphText).append("\n");
                }
            }

            return text.toString().trim();
        }
    }

    /**
     * 解析 .doc 格式（旧版Word）
     */
    private String parseDoc(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {

            String text = extractor.getText();
            return text != null ? text.trim() : "";
        }
    }
}
