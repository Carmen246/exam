package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从旧版 Word .doc 文件提取文本。
 */
@Component
public class DocTextExtractor {

    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*(\\d+)\\s*[\\.．、]\\s*.*");
    private static final Pattern OPTION_LINE = Pattern.compile("^\\s*[A-Ha-h]\\s*[\\.、)）]\\s*.*");

    @Autowired
    private FillProgramBlankProcessor blankProcessor;

    public String extract(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            String text = normalizeText(extractor.getText());
            if (StringUtils.isBlank(text)) {
                throw new IOException("DOC 未提取到文字，请确认文件不是图片扫描件或受保护文档");
            }
            text = restoreMissingQuestionNumbers(text);
            return blankProcessor.markSpacePaddedBlanks(text);
        }
    }

    String restoreMissingQuestionNumbers(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder result = new StringBuilder();
        Integer currentSectionType = null;
        int currentNumber = 0;
        String previousContentLine = "";

        for (String line : lines) {
            String trimmed = line.trim();
            Integer inferredType = inferSectionType(trimmed);
            if (inferredType != null) {
                currentSectionType = inferredType;
                currentNumber = 0;
                appendLine(result, line);
                previousContentLine = trimmed;
                continue;
            }

            Matcher numbered = NUMBERED_LINE.matcher(trimmed);
            if (numbered.matches()) {
                currentNumber = Math.max(currentNumber, Integer.parseInt(numbered.group(1)));
                appendLine(result, line);
                previousContentLine = trimmed;
                continue;
            }

            if (shouldRestoreQuestionNumber(currentSectionType, trimmed, previousContentLine)) {
                currentNumber++;
                appendLine(result, line.replaceFirst("^\\s*", "$0" + currentNumber + ". "));
            } else {
                appendLine(result, line);
            }

            if (StringUtils.isNotBlank(trimmed)) {
                previousContentLine = trimmed;
            }
        }

        return result.toString().trim();
    }

    private Integer inferSectionType(String line) {
        if (StringUtils.isBlank(line)) {
            return null;
        }
        if (!line.matches(".*(判断题|单选题|多选题|填空题|程序填空题|程序阅读题|阅读程序写结果题|程序设计题|编程题|程序改错题|综合应用题).*")) {
            return null;
        }
        if (line.matches(".*本大题共.*") || QuestionBoundaryHelper.isSectionHeaderOnlyFragment(line)) {
            return QuestionBoundaryHelper.inferQuTypeFromSection(line);
        }
        return null;
    }

    private boolean shouldRestoreQuestionNumber(Integer sectionType, String line, String previousContentLine) {
        if (sectionType == null || StringUtils.isBlank(line)) {
            return false;
        }
        if (OPTION_LINE.matcher(line).matches()
                || line.startsWith("答案：")
                || line.startsWith("参考答案：")
                || line.startsWith("解析：")
                || isIgnoredDocLine(line)
                || isCodeLikeLine(line)) {
            return false;
        }

        if (Integer.valueOf(1).equals(sectionType)
                || Integer.valueOf(2).equals(sectionType)
                || Integer.valueOf(3).equals(sectionType)) {
            return line.matches(".*(（\\s*）|\\(\\s*\\)|_{3,}).*")
                    || line.matches(".*(下列|以下|下面|若|如果|在C语言中|C语言规定).*");
        }

        if (Integer.valueOf(7).equals(sectionType)) {
            if (StringUtils.defaultString(previousContentLine).matches(".*[：:]\\s*$")) {
                return false;
            }
            return line.matches(".*(编写|写程序|程序设计|实现|用\\s*for|求|计算|输入|输出|读入|写入|文件|成绩|学生|函数).*");
        }

        return false;
    }

    private boolean isIgnoredDocLine(String line) {
        return "得分".equals(line)
                || line.matches("^(题序|总分|签名|考试科目|考试类型|考试方式|完成时限|拟题人|审核人|批准人).*")
                || line.matches("^(应将全部答案写在答卷纸|编程题应写明题号|考试完成后|不要另添卷纸|否则作无效处理|写在背面).*");
    }

    private boolean isCodeLikeLine(String line) {
        return line.startsWith("#")
                || line.matches("^\\s*(int|void|char|float|double|long|short|unsigned)\\s+.*")
                || line.contains("{")
                || line.contains("}")
                || line.matches(".*;\\s*$");
    }

    private void appendLine(StringBuilder result, String line) {
        result.append(line).append("\n");
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
