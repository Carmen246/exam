package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * 从 PDF 提取文本，供 AI 导入解析使用（需含可选中文字层，扫描件不支持）
 */
@Component
public class PdfTextExtractor {

    public String extract(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            if (document.getNumberOfPages() == 0) {
                throw new IOException("PDF 文件没有页面");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = normalizeText(stripper.getText(document));
            if (StringUtils.isBlank(text)) {
                throw new IOException("PDF 未提取到文字，可能是扫描件图片，请使用含文字层的 PDF 或转为 docx");
            }
            return text;
        }
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.trim();
    }
}
