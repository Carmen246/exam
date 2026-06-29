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
import java.util.ArrayList;
import java.util.List;

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
                return cleanQuestionText(new String(file.getBytes(), StandardCharsets.UTF_8));
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

            return cleanQuestionText(text.toString());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void appendLine(StringBuilder builder, String line) {
        if (StringUtils.isNotBlank(line)) {
            builder.append(line).append("\n");
        }
    }

    private String cleanQuestionText(String rawText) {
        if (StringUtils.isBlank(rawText)) {
            return "";
        }

        String text = rawText;
        text = text.replace("\r\n", "\n");
        text = text.replace("\r", "\n");
        text = text.replace("\t", " ");
        text = text.replace("\u00A0", " ");
        text = text.replace("\u3000", " ");
        text = text.replace("．", ".");
        text = text.replace("：", "：");

        // 如果一行里挤了多个选项，先拆开。
        text = text.replaceAll("\\s+([A-Ha-h])\\s*[\\.、]\\s*", "\n$1. ");

        // 如果答案跟在同一行后面，也拆出来。
        text = text.replaceAll("\\s+(答案|参考答案)\\s*[:：]\\s*", "\n答案：");

        String[] rawLines = text.split("\n");
        List<String> lines = new ArrayList<>();

        for (String rawLine : rawLines) {
            String line = normalizeLine(rawLine);
            if (StringUtils.isBlank(line)) {
                continue;
            }

            if (lines.isEmpty() || isNewBlock(line)) {
                lines.add(line);
            } else {
                int lastIndex = lines.size() - 1;
                lines.set(lastIndex, mergeLine(lines.get(lastIndex), line));
            }
        }

        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            result.append(line).append("\n");
        }

        return result.toString().trim();
    }

    private String normalizeLine(String line) {
        if (line == null) {
            return "";
        }

        String value = line.trim();

        value = value.replaceAll("[ ]+", " ");

        // 去掉中文之间因为 Word 排版产生的空格：属 于 -> 属于，操作 系统 -> 操作系统
        value = value.replaceAll("([\\u4e00-\\u9fa5])\\s+([\\u4e00-\\u9fa5])", "$1$2");

        // 统一题号：1、 1． 1. 都变成 1.
        value = value.replaceAll("^(\\d+)\\s*[\\.、]\\s*", "$1. ");

        // 统一选项：A、 A． A. 都变成 A.
        value = value.replaceAll("^([A-Ha-h])\\s*[\\.、]\\s*", "$1. ");

        // 统一答案
        value = value.replaceAll("^(答案|参考答案)\\s*[:：]?\\s*", "答案：");

        value = removeOptionTailIndex(value);

        return value.trim();
    }

    private String removeOptionTailIndex(String line) {
        if (StringUtils.isBlank(line)) {
            return line;
        }

        String value = line.trim();

        String[] optionLetters = {"A", "B", "C", "D"};
        for (int i = 0; i < optionLetters.length; i++) {
            String letter = optionLetters[i];
            String tailNumber = String.valueOf(i + 1);

            String prefix = letter + ". ";
            if (value.startsWith(prefix) && value.endsWith(" " + tailNumber)) {
                return value.substring(0, value.length() - tailNumber.length()).trim();
            }
        }

        return value;
    }

    private boolean isNewBlock(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }

        return line.matches("^\\d+\\.\\s*.*")
                || line.matches("^[A-Ha-h]\\.\\s*.*")
                || line.startsWith("答案：")
                || line.startsWith("解析：");
    }

    private String mergeLine(String previous, String current) {
        if (StringUtils.isBlank(previous)) {
            return current;
        }

        if (StringUtils.isBlank(current)) {
            return previous;
        }

        String last = previous.substring(previous.length() - 1);
        String first = current.substring(0, 1);

        if (last.matches("[\\u4e00-\\u9fa5]") && first.matches("[\\u4e00-\\u9fa5]")) {
            return previous + current;
        }

        return previous + " " + current;
    }
}