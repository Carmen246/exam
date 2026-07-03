package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 试题文本本地规则清洗（不调用 AI）
 */
@Component
public class QuestionTextLocalNormalizer {

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
        text = QuestionBoundaryHelper.separateGluedBoundaries(text);
        text = QuestionBoundaryHelper.stripInlineFilledAnswer(text);
        text = text.replaceAll("\\s+([A-Ha-h])\\s*[\\.、)）]\\s*", "\n$1. ");
        text = text.replaceAll("\\s+(答案|参考答案)\\s*[:：]\\s*", "\n答案：");

        String[] rawLines = text.split("\n");
        List<String> lines = new ArrayList<>();

        for (String rawLine : rawLines) {
            String line = normalizeLine(rawLine);
            if (StringUtils.isBlank(line) || isPhantomOptionLine(line)) {
                continue;
            }

            if (!lines.isEmpty() && isBareOptionLabel(lines.get(lines.size() - 1))) {
                int lastIndex = lines.size() - 1;
                lines.set(lastIndex, mergeLine(lines.get(lastIndex), line));
                continue;
            }

            if (lines.isEmpty() || QuestionBoundaryHelper.isNewContentLine(line)) {
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
        return QuestionBoundaryHelper.splitQuestionBlocks(text).size();
    }

    private String normalizeLine(String line) {
        if (line == null) {
            return "";
        }

        String value = line.trim();
        if (!containsFillMarker(value) && !looksLikeCodeLine(value)) {
            value = value.replaceAll("[ ]+", " ");
        }
        value = value.replaceAll("([\\u4e00-\\u9fa5])\\s+([\\u4e00-\\u9fa5])", "$1$2");
        if (!QuestionBoundaryHelper.isBlankSubNumberLine(value)) {
            value = value.replaceAll("^(\\d+)\\s*[\\.、]\\s*", "$1. ");
        }
        value = value.replaceAll("^([A-Ha-h])\\s*[\\.、)）]\\s*", "$1. ");
        value = value.replaceAll("^(答案|参考答案)\\s*[:：]?\\s*", "答案：");
        value = removeOptionTailIndex(value);
        return value.trim();
    }

    private boolean containsFillMarker(String line) {
        return line != null && line.contains("{FILL:");
    }

    private boolean looksLikeCodeLine(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        return line.contains("{FILL:")
                || line.contains("#include")
                || line.contains("#define")
                || line.contains("{")
                || line.contains("}")
                || line.contains("____")
                || line.matches(".*\\b(if|else|for|while|return|int|void|char|float|double|printf|scanf)\\b.*");
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
                String optionBody = value.substring(prefix.length(), value.length() - tailNumber.length()).trim();
                if (StringUtils.isBlank(optionBody)) {
                    continue;
                }
                return value.substring(0, value.length() - tailNumber.length()).trim();
            }
        }
        return value;
    }

    private boolean isPhantomOptionLine(String line) {
        return line != null && line.trim().matches("^[A-D]\\.\\s*[。．.]$");
    }

    private boolean isBareOptionLabel(String line) {
        return line != null && line.trim().matches("^[A-D]\\.\\s*$");
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
