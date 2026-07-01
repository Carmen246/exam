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
                    if (StringUtils.isNotBlank(block) && !isNonQuestionFragment(block)) {
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
        return isNonQuestionFragment(block);
    }

    public static boolean isNonQuestionFragment(String block) {
        if (StringUtils.isBlank(block)) {
            return true;
        }
        String value = block.replaceAll("\\s+", " ").trim();
        if (looksLikeCoverPageBlock(value)) {
            return true;
        }
        if (looksLikeHeaderOrScoreBlock(value)) {
            return true;
        }
        if (looksLikeSectionOnlyBlock(value)) {
            return true;
        }
        if (looksLikeInstructionBlock(value)) {
            return true;
        }

        boolean hasAnswerSheetWords = value.contains("得分")
                || value.contains("请在各小题正确选项")
                || value.contains("对应位置")
                || value.contains("本大题共");
        boolean hasOptionHeader = value.matches("(?s).*\\bA\\s+B\\s+C\\s+D\\b.*")
                || value.matches("(?s).*A、B、C、D.*");
        boolean hasRealQuestionCue = value.contains("{FILL:")
                || value.contains("#include")
                || value.matches("(?s).*\\b(main|printf|scanf|return|if|for|while)\\b.*")
                || value.matches("(?s).*(以下|下面|阅读|编写|输出|功能|结果).*");
        return hasAnswerSheetWords && hasOptionHeader && !hasRealQuestionCue;
    }

    private static boolean looksLikeCoverPageBlock(String value) {
        boolean hasCoverWords = value.contains("浙江科技学院")
                || value.contains("考试科目")
                || value.contains("考试类型")
                || value.contains("考试方式")
                || value.contains("完成时限")
                || value.contains("拟题人")
                || value.contains("审核人")
                || value.contains("批准人");
        boolean hasScoreTableWords = value.contains("题序")
                || value.contains("总分")
                || value.contains("加分人")
                || value.contains("复核人");
        return hasCoverWords && hasScoreTableWords;
    }

    private static boolean looksLikeHeaderOrScoreBlock(String value) {
        boolean hasHeaderWords = value.contains("批准人")
                || value.contains("学院")
                || value.contains("年级")
                || value.contains("专业")
                || value.contains("题序")
                || value.contains("签名")
                || value.contains("得分");
        return hasHeaderWords && !hasRealQuestionContent(value);
    }

    private static boolean looksLikeSectionOnlyBlock(String value) {
        boolean hasSectionWords = value.matches("(?s).*(判断题|单选题|多选题|程序阅读题|程序设计题|程序填空题).*本大题共.*")
                || value.contains("请在各小题正确选项")
                || value.contains("对应位置");
        boolean hasGridWords = value.matches("(?s).*\\b(?:A\\s+B\\s+C\\s+D|T\\s+F)\\b.*")
                || value.matches("(?s).*\\b1\\s+2\\s+3\\s+4\\s+5\\b.*");
        boolean hasConcreteQuestion = value.contains("{FILL:")
                || value.contains("#include")
                || value.matches("(?s).*\\b(main|printf|scanf|return|if|for|while)\\b.*")
                || value.matches("(?s).*(（\\s*）|\\(\\s*\\)).*")
                || value.matches("(?s).*(以下程序|下面程序|以下函数|下面函数|输出结果|运行结果|输入半径|查找字符串).*");
        return hasSectionWords && hasGridWords && !hasConcreteQuestion;
    }

    /**
     * 检测考试说明/须知文本块（命题人、说明、答题须知等），这些不是试题内容
     */
    private static boolean looksLikeInstructionBlock(String value) {
        boolean hasInstructionKeywords = value.contains("应将全部答案写在答卷纸")
                || value.contains("编程题应写明题号")
                || value.contains("考试完成后")
                || value.contains("不要另添卷纸")
                || value.matches("(?s)^\\s*命题[：:]\\s*.*")
                || value.matches("(?s)^\\s*说明[：:]\\s*.*")
                || value.contains("否则作无效处理")
                || value.contains("写在背面");
        return hasInstructionKeywords && !hasRealQuestionContent(value);
    }

    private static boolean hasRealQuestionContent(String value) {
        return value.contains("{FILL:")
                || value.contains("#include")
                || value.matches("(?s).*\\b(main|printf|scanf|return|if|for|while|fopen|fclose|fread|fwrite|fprintf|fscanf)\\b.*")
                || value.matches("(?s).*(（\\s*）|\\(\\s*\\)).*")
                || value.matches("(?s).*(以下|下面|阅读|编写|输出|功能|结果|求|查找|输入|返回|读入|写入|计算|平均|存放|文件|成绩|学生|打开|关闭|读取|保存|数组|指针|结构体|函数|循环|排序|删除|插入|修改).*");
    }

    /**
     * 将试题文本中的分区编号转换为全局连续编号。
     * 试卷中每个题型（判断题、单选题等）的题号通常从1开始，
     * 但答案文档已转为连续编号，因此需要同步转换。
     * 偏移量基于每个题型中实际出现的最大题号（而非标题中的小题数），
     * 以正确处理程序填空等含子编号的题型。
     * 如果检测到试卷已使用连续编号（某题型首题题号>1），则不做任何偏移。
     */
    public static String renumberContinuous(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }

        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        // 匹配题型标题，中文数字前缀可选（有些试卷省略"三、"等前缀）
        // 排除答题纸模板标题（含"请在各小题正确选项"）
        java.util.regex.Pattern sectionPat = java.util.regex.Pattern.compile(
                "(?m)^(?:[一二三四五六七八九十]+、)?\\s*(?:判断题|单选题|多选题|程序填空题|程序阅读题|程序设计题).*本大题共\\d+小题(?!.*请在各小题正确选项).*$");

        StringBuilder result = new StringBuilder();
        int offset = 0;
        int currentSectionMaxQ = 0;
        int pos = 0;
        boolean continuousNumberingDetected = false;

        java.util.regex.Matcher secMatcher = sectionPat.matcher(normalized);
        while (secMatcher.find()) {
            // 处理题型标题之前的文本：对题号加上偏移量
            String before = normalized.substring(pos, secMatcher.start());

            // 记录当前题型中的最大题号（用于计算下一题型的偏移量）
            int maxQInSection = getMaxQuestionNumberInText(before);
            int firstQInSection = getFirstQuestionNumberInText(before);
            currentSectionMaxQ = Math.max(currentSectionMaxQ, maxQInSection);

            // 检测连续编号：如果某题型首题题号>1，说明试卷已使用连续编号
            if (firstQInSection > 1) {
                continuousNumberingDetected = true;
            }

            if (continuousNumberingDetected) {
                // 试卷已使用连续编号，不做偏移，直接保留原文
                result.append(before);
                offset = maxQInSection;
            } else {
                // 分区编号模式，对题号加上偏移量
                result.append(applyOffset(before, offset));
                offset += currentSectionMaxQ;
            }
            currentSectionMaxQ = 0;

            // 保留题型标题行原文
            result.append(secMatcher.group());
            pos = secMatcher.end();
        }

        // 处理最后一段文本
        if (pos < normalized.length()) {
            if (continuousNumberingDetected) {
                result.append(normalized.substring(pos));
            } else {
                result.append(applyOffset(normalized.substring(pos), offset));
            }
        }

        return result.toString();
    }

    /**
     * 对文本段中的题号统一加上偏移量，并返回处理后该段的最大题号
     */
    private static String applyOffset(String text, int offset) {
        if (offset == 0) {
            return text;
        }
        java.util.regex.Pattern qNumPat = java.util.regex.Pattern.compile(
                "^(\\s*)(\\d+)(\\s*[.．、])", java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher m = qNumPat.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int num = Integer.parseInt(m.group(2));
            m.appendReplacement(sb,
                    java.util.regex.Matcher.quoteReplacement(m.group(1) + (num + offset) + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 从文本中提取最大的题号数字，跳过指令文本行
     */
    private static int getMaxQuestionNumberInText(String text) {
        java.util.regex.Pattern qNumPat = java.util.regex.Pattern.compile(
                "^(\\s*)(\\d+)(\\s*[.．、])");
        int max = 0;
        for (String line : text.split("\n")) {
            java.util.regex.Matcher m = qNumPat.matcher(line);
            if (m.find() && !isInstructionLine(line)) {
                max = Math.max(max, Integer.parseInt(m.group(2)));
            }
        }
        return max;
    }

    /**
     * 从文本中提取第一个题号数字，用于检测试卷是否已使用连续编号。跳过指令文本行。
     */
    private static int getFirstQuestionNumberInText(String text) {
        java.util.regex.Pattern qNumPat = java.util.regex.Pattern.compile(
                "^(\\s*)(\\d+)(\\s*[.．、])");
        for (String line : text.split("\n")) {
            java.util.regex.Matcher m = qNumPat.matcher(line);
            if (m.find() && !isInstructionLine(line)) {
                return Integer.parseInt(m.group(2));
            }
        }
        return 0;
    }

    /**
     * 判断某行是否为考试指令文本（非试题），如"1. 应将全部答案写在答卷纸..."
     * 这些行虽然以数字开头，但不是真正的题号
     */
    private static boolean isInstructionLine(String numberedLine) {
        if (numberedLine == null) {
            return false;
        }
        String content = numberedLine.replaceAll("^\\s*\\d+\\s*[.．、]\\s*", "").trim();
        return content.contains("应将全部答案写在答卷纸")
                || content.contains("编程题应写明题号")
                || content.contains("考试完成后")
                || content.contains("不要另添卷纸")
                || content.contains("否则作无效处理")
                || content.contains("写在背面")
                || content.contains("必须将试卷与答卷同时交回");
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
