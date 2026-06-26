package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.service.QuestionDocumentParseService;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class QuestionDocumentParseServiceImpl implements QuestionDocumentParseService {

    @Override
    public String parseText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空！");
        }

        String fileName = file.getOriginalFilename();
        String ext = FilenameUtils.getExtension(fileName);

        if (StringUtils.isBlank(ext)) {
            throw new ServiceException("无法识别文件类型！");
        }

        try {
            if ("docx".equalsIgnoreCase(ext)) {
                return parseDocx(file.getInputStream());
            }

            if ("txt".equalsIgnoreCase(ext)) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }

            throw new ServiceException("暂只支持 docx、txt 文件！");
        } catch (Exception e) {
            throw new ServiceException("试题文档解析失败：" + e.getMessage());
        }
    }

    private String parseDocx(InputStream inputStream) throws Exception {
        StringBuilder text = new StringBuilder();

        try {
            XWPFDocument document = new XWPFDocument(inputStream);

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                appendLine(text, paragraph.getText());
            }

            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        appendLine(text, cell.getText());
                    }
                }
            }

            return text.toString().trim();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void appendLine(StringBuilder builder, String line) {
        if (StringUtils.isNotBlank(line)) {
            builder.append(line.trim()).append("\n");
        }
    }
}