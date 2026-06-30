package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 题干与程序代码拆分、C 程序排版
 */
@Component
public class ProgramContentFormatter {

    private static final Pattern CODE_START_PATTERN = Pattern.compile(
            "(?m)^\\s*(#include\\s|#define\\s|"
                    + "(?:int|char|void|float|double|long|short|unsigned)\\s+\\w+\\s*(?:=|\\()|"
                    + "main\\s*\\()");

    private static final Pattern RUN_RESULT_SUFFIX = Pattern.compile("运行结果\\s*[:：]\\s*.+$");
    private static final Pattern INLINE_INSTRUCTION = Pattern.compile("阅读程序[，,]?\\s*|写出运行结果[。.]?\\s*");

    public static class StemCodeParts {
        private final String stem;
        private final String code;

        public StemCodeParts(String stem, String code) {
            this.stem = stem;
            this.code = code;
        }

        public String getStem() {
            return stem;
        }

        public String getCode() {
            return code;
        }
    }

    public StemCodeParts splitStemAndCode(String content) {
        if (StringUtils.isBlank(content)) {
            return new StemCodeParts("", "");
        }

        String text = content.trim();
        text = RUN_RESULT_SUFFIX.matcher(text).replaceAll("").trim();

        int codeStart = findCodeBlockStart(text);
        if (codeStart > 0) {
            String stem = cleanStem(text.substring(0, codeStart));
            String code = formatProgramCode(text.substring(codeStart));
            return new StemCodeParts(stem, code);
        }

        if (codeStart == 0) {
            return new StemCodeParts("", formatProgramCode(text));
        }

        return new StemCodeParts(cleanStem(text), "");
    }

    public String mergeStemAndCode(String stem, String code) {
        String cleanStem = cleanStem(stem);
        String cleanCode = formatProgramCode(code);
        if (StringUtils.isNotBlank(cleanStem) && StringUtils.isNotBlank(cleanCode)) {
            return cleanStem + "\n\n" + cleanCode;
        }
        if (StringUtils.isNotBlank(cleanCode)) {
            return cleanCode;
        }
        return cleanStem;
    }

    public String cleanStem(String stem) {
        if (StringUtils.isBlank(stem)) {
            return "";
        }
        String value = stem.trim();
        value = value.replaceAll("(?m)^\\s*\\d+[\\.．、]\\s*", "");
        value = RUN_RESULT_SUFFIX.matcher(value).replaceAll("").trim();
        value = value.replaceAll("\\s+", " ");
        return value.trim();
    }

    public String formatProgramCode(String code) {
        if (StringUtils.isBlank(code)) {
            return "";
        }

        String text = code.trim();
        text = INLINE_INSTRUCTION.matcher(text).replaceAll("");
        text = RUN_RESULT_SUFFIX.matcher(text).replaceAll("").trim();
        text = text.replaceAll("(?m)^[\\u4e00-\\u9fa5，。；：、！？\\s…]+$", "").trim();

        if (needsLayoutNormalize(text)) {
            text = expandCompactCCode(text);
        }

        return indentCode(text);
    }

    private boolean needsLayoutNormalize(String text) {
        if (!text.contains("{") && !text.contains(";")) {
            return false;
        }
        for (String line : text.split("\n")) {
            String value = line.trim();
            if (StringUtils.isBlank(value)) {
                continue;
            }
            if (value.matches(".*\\{[^}]*;.*")) {
                return true;
            }
            if (value.length() > 48 && value.contains(";")) {
                return true;
            }
        }
        return false;
    }

    private String expandCompactCCode(String text) {
        String expanded = text.replaceAll(";\\s*", ";\n");
        expanded = expanded.replaceAll("\\{\\s*", " {\n");
        expanded = expanded.replaceAll("\\}\\s*", "\n}\n");
        return expanded;
    }

    public int findCodeBlockStart(String text) {
        if (StringUtils.isBlank(text)) {
            return -1;
        }
        Matcher matcher = CODE_START_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.start();
        }
        String[] markers = {"#include", "void main", "int main", "main()", "main (", "public class"};
        int earliest = -1;
        for (String marker : markers) {
            int idx = indexOfAtLineStart(text, marker);
            if (idx >= 0 && (earliest < 0 || idx < earliest)) {
                earliest = idx;
            }
        }
        return earliest;
    }

    private int indexOfAtLineStart(String text, String marker) {
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int idx = text.indexOf(marker, searchFrom);
            if (idx < 0) {
                return -1;
            }
            if (idx == 0 || text.charAt(idx - 1) == '\n') {
                return idx;
            }
            searchFrom = idx + 1;
        }
        return -1;
    }

    private String indentCode(String code) {
        String[] lines = code.replace("\r\n", "\n").split("\n");
        StringBuilder sb = new StringBuilder();
        int level = 0;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (StringUtils.isBlank(line)) {
                continue;
            }
            if (line.startsWith("}")) {
                level = Math.max(0, level - 1);
            }
            for (int i = 0; i < level; i++) {
                sb.append("  ");
            }
            sb.append(line).append('\n');
            if (line.endsWith("{")) {
                level++;
            }
        }
        return sb.toString().trim();
    }
}
