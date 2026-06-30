package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 试题边界识别：区分真正题号与程序填空中 (3)(4)(5) 空位编号
 */
public final class QuestionBoundaryHelper {

    private static final Pattern NUMBERED_QUESTION_START = Pattern.compile("(?m)(?=^\\s*\\d+[\\.．、]\\s*)");

    private static final Pattern STEM_QUESTION_START = Pattern.compile(
            "(?m)(?=^\\s*(得分|[一二三四五六七八九十]+、|程序填空题|程序阅读题|程序设计题|判断题|单选题|多选题|"
                    + "以下函数|以下程序|下面函数|下面程序的功能|阅读程序|编写程序|输出以下|程序改错|改正下面的程序))");

    private static final Pattern GLUED_OPTION_TO_STEM = Pattern.compile(
            "(?m)([A-D]\\.\\s*[^\\n]*?)(以下函数|以下程序|下面函数|下面程序的功能|阅读程序|编写程序|输出以下|程序改错|改正下面的程序)");

    private static final Pattern GLUED_OPTION_TO_NUMBERED_STEM = Pattern.compile(
            "(?m)([A-D]\\.\\s*[^\\n]*?)(\\d+\\.\\s*[\u4e00-\u9fa5])");

    private static final Pattern GLUED_OPTION_TO_UNNUMBERED_STEM = Pattern.compile(
            "(?m)([A-D]\\.\\s*[^\\n]*?)(下面程序|以下函数)");

    private static final Pattern GLUED_OPTION_TO_SECTION = Pattern.compile(
            "(?m)([A-D]\\.\\s*[^\\n]*?)(\\s*得分\\s*(?:[一二三四五六七八九十]+、)?\\s*"
                    + "(?:程序填空题|程序阅读题|程序设计题|判断题|单选题|多选题))");

    private static final Pattern UNNUMBERED_CHOICE_STEM = Pattern.compile(
            "(?m)(?=^\\s*(?:[\\u4e00-\\u9fa5]|[A-Za-z_][A-Za-z0-9_]*\\s*\\([^\\n)]*\\)\\s*函数)"
                    + "[^\\n]{3,160}(?:（\\s*）|\\(\\s*\\)|_{3,})[^\\n]*[。？?]?$)");

    private QuestionBoundaryHelper() {
    }

    public static String separateGluedBoundaries(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        String value = text;
        value = GLUED_OPTION_TO_STEM.matcher(value).replaceAll("$1\n$2");
        value = GLUED_OPTION_TO_NUMBERED_STEM.matcher(value).replaceAll("$1\n$2");
        value = GLUED_OPTION_TO_UNNUMBERED_STEM.matcher(value).replaceAll("$1\n$2");
        value = GLUED_OPTION_TO_SECTION.matcher(value).replaceAll("$1\n$2");
        return value;
    }

    public static List<String> splitQuestionBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return blocks;
        }

        String normalized = separateGluedBoundaries(text).replace("\r\n", "\n").replace("\r", "\n");
        String[] numberedParts = NUMBERED_QUESTION_START.split(normalized);
        for (String numberedPart : numberedParts) {
            if (StringUtils.isBlank(numberedPart)) {
                continue;
            }
            String[] stemParts = STEM_QUESTION_START.split(numberedPart.trim());
            for (String stemPart : stemParts) {
                for (String choicePart : splitUnnumberedChoiceBlocks(stemPart)) {
                    String block = choicePart.trim();
                    if (StringUtils.isNotBlank(block) && !isAnswerSheetFragment(block)) {
                        blocks.add(block);
                    }
                }
            }
        }
        return blocks;
    }

    private static List<String> splitUnnumberedChoiceBlocks(String text) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return result;
        }
        Matcher matcher = UNNUMBERED_CHOICE_STEM.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
        }
        if (starts.size() <= 1) {
            result.add(text);
            return result;
        }
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            String part = text.substring(start, end).trim();
            if (StringUtils.isNotBlank(part)) {
                result.add(part);
            }
        }
        return result;
    }

    public static boolean isAnswerSheetFragment(String block) {
        if (StringUtils.isBlank(block)) {
            return true;
        }
        String value = block.replaceAll("\\s+", " ").trim();
        boolean hasAnswerSheetWords = value.contains("得分")
                || value.contains("请在各小题正确选项")
                || value.contains("对应位置")
                || value.contains("本大题共");
        boolean hasOptionHeader = value.matches("(?s).*\\bA\\s+B\\s+C\\s+D\\b.*")
                || value.matches("(?s).*A、B、C、D.*");
        boolean hasRealQuestionCue = value.contains("____")
                || value.contains("{FILL:")
                || value.contains("#include")
                || value.matches("(?s).*\\b(main|printf|scanf|return|if|for|while)\\b.*")
                || value.matches("(?s).*(以下|下面|阅读|编写|输出|功能|结果).*");
        return hasAnswerSheetWords && hasOptionHeader && !hasRealQuestionCue;
    }

    public static boolean isBlankSubNumberLine(String line) {
        return StringUtils.isNotBlank(line) && line.trim().matches("^[（(]\\s*\\d+\\s*[）)]\\s*$");
    }

    public static boolean isQuestionStemStart(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        String value = line.trim();
        if (looksLikeUnnumberedChoiceStem(value)) {
            return true;
        }
        return value.matches("^\\d+\\.\\s*.*")
                || value.matches("^(得分|[一二三四五六七八九十]+、|程序填空题|程序阅读题|程序设计题|判断题|单选题|多选题).*")
                || value.matches("^(以下函数|以下程序|下面函数|下面程序的功能|阅读程序|编写程序|输出以下|程序改错|改正下面的程序).*");
    }

    private static boolean looksLikeUnnumberedChoiceStem(String value) {
        return value.matches("^(?:[\\u4e00-\\u9fa5]|[A-Za-z_][A-Za-z0-9_]*\\s*\\([^)]*\\)\\s*函数).{3,160}(?:（\\s*）|\\(\\s*\\)|_{3,}).*");
    }

    public static boolean isNewContentLine(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        if (isBlankSubNumberLine(line)) {
            return true;
        }
        return isQuestionStemStart(line)
                || line.matches("^[A-Ha-h]\\.\\s*.*")
                || line.startsWith("答案：")
                || line.startsWith("解析：")
                || line.startsWith("参考答案：")
                || looksLikeCodeLine(line);
    }

    private static boolean looksLikeCodeLine(String line) {
        return line.contains("{FILL:")
                || line.contains("____")
                || line.contains("#include")
                || line.matches(".*\\b(main|if|else|for|while|return|int|void|char|printf|scanf)\\b.*");
    }
}
