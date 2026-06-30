package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 试题文本本地规则清洗（不调用 AI）
 */
@Component
public class QuestionTextLocalNormalizer {

    private static final Pattern QUESTION_START_PATTERN = Pattern.compile("(?m)(?=^\\s*\\d+[\\.．、)\\）]\\s*)");

    public String normalize(String rawText) {
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
        text = text.replaceAll("\\s+([A-Ha-h])\\s*[\\.、)\\）]\\s*", "\n$1. ");
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

    public int countQuestionBlocks(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }

        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = QUESTION_START_PATTERN.split(normalized);
        int count = 0;
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                count++;
            }
        }
        return count;
    }

    private String normalizeLine(String line) {
        if (line == null) {
            return "";
        }

        String value = line.trim();
        value = value.replaceAll("[ ]+", " ");
        value = value.replaceAll("([\\u4e00-\\u9fa5])\\s+([\\u4e00-\\u9fa5])", "$1$2");
        value = value.replaceAll("^(\\d+)\\s*[\\.、)\\）]\\s*", "$1. ");
        value = value.replaceAll("^([A-Ha-h])\\s*[\\.、)\\）]\\s*", "$1. ");
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
