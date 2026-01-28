package com.example.pkqb.service;

import com.example.pkqb.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * PDF 文档解析服务
 */
@Slf4j
@Service
public class PdfParserService {

    /**
     * 解析 PDF 文档为文本
     *
     * @param filePath 文件路径
     * @return 解析后的文本
     */
    public String parse(String filePath) {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text.trim();
        } catch (IOException e) {
            log.error("解析 PDF 文档失败", e);
            throw new BusinessException("解析 PDF 文档失败: " + e.getMessage());
        }
    }
}
