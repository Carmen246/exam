package com.yf.exam.modules.qu.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.yf.exam.modules.qu.dto.QuAnswerDTO;
import com.yf.exam.modules.qu.dto.QuAnswerOptionDTO;
import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 程序填空题：各空 A/B/C/D 候选项的提取、挂载与 remark 持久化
 */
public final class FillProgramBlankOptionSupport {

    public static final String REMARK_PREFIX = "@fillBlankOpts:";

    private static final Pattern FILL_BLANK_NUM = Pattern.compile("(?m)^\\s*[（(](\\d+)[)）]\\s*$");
    private static final Pattern FILL_BLANK_OPTION_LINE = Pattern.compile(
            "^\\s*[（(](\\d+)[)）]\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern FILL_OPTION_ITEM = Pattern.compile(
            "(?s)([A-D])\\s*[\\.、．]\\s*(.*?)(?=\\s+[A-D]\\s*[\\.、．]\\s*|$)");
    private static final Pattern FILL_OPTION_LINE = Pattern.compile("^\\s*([A-D])\\s*[.、．]\\s*(.+)$");

    private FillProgramBlankOptionSupport() {
    }

    public static Map<Integer, Map<String, String>> extractFillBlankOptions(String content) {
        Map<Integer, Map<String, String>> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(content)) {
            return result;
        }

        Matcher blockMatcher = FILL_BLANK_OPTION_LINE.matcher(content);
        while (blockMatcher.find()) {
            String rest = blockMatcher.group(2).trim();
            if (!FILL_OPTION_ITEM.matcher(rest).find()) {
                continue;
            }
            int blankNum = Integer.parseInt(blockMatcher.group(1));
            Map<String, String> opts = result.computeIfAbsent(blankNum, key -> new LinkedHashMap<>());
            parseInlineOptions(rest, opts);
        }

        String[] lines = content.split("\\r?\\n");
        int currentBlank = -1;
        for (String line : lines) {
            String trimmed = line.trim();
            if (StringUtils.isBlank(trimmed)) {
                continue;
            }

            Matcher inlineMatcher = FILL_BLANK_OPTION_LINE.matcher(trimmed);
            if (inlineMatcher.matches()) {
                int blankNum = Integer.parseInt(inlineMatcher.group(1));
                Map<String, String> opts = result.computeIfAbsent(blankNum, key -> new LinkedHashMap<>());
                parseInlineOptions(inlineMatcher.group(2), opts);
                currentBlank = blankNum;
                continue;
            }

            Matcher blankMatcher = FILL_BLANK_NUM.matcher(trimmed);
            if (blankMatcher.matches()) {
                currentBlank = Integer.parseInt(blankMatcher.group(1));
                result.putIfAbsent(currentBlank, new LinkedHashMap<>());
                continue;
            }

            if (currentBlank > 0 && result.containsKey(currentBlank)) {
                Matcher optMatcher = FILL_OPTION_LINE.matcher(trimmed);
                if (optMatcher.matches()) {
                    result.get(currentBlank).put(optMatcher.group(1), optMatcher.group(2).trim());
                }
            }
        }
        return result;
    }

    /**
     * 优先从题目 content 提取各空候选项；若 AI 已剥离选项文本，则回退到原始 chunk 中定位本题再提取。
     */
    public static Map<Integer, Map<String, String>> extractOptionsForQuestion(QuDetailDTO qu,
                                                                              String content,
                                                                              String sourceText,
                                                                              int questionIndex) {
        Map<Integer, Map<String, String>> fromContent = extractFillBlankOptions(content);
        if (!fromContent.isEmpty()) {
            return fromContent;
        }
        if (StringUtils.isBlank(sourceText)) {
            return fromContent;
        }

        List<String> blocks = QuestionBoundaryHelper.splitQuestionBlocks(sourceText);
        if (questionIndex > 0 && questionIndex <= blocks.size()) {
            Map<Integer, Map<String, String>> byIndex = extractFillBlankOptions(blocks.get(questionIndex - 1));
            if (!byIndex.isEmpty()) {
                return byIndex;
            }
        }

        for (String block : blocks) {
            if (!matchesQuestionBlock(qu, content, block)) {
                continue;
            }
            Map<Integer, Map<String, String>> matched = extractFillBlankOptions(block);
            if (!matched.isEmpty()) {
                return matched;
            }
        }

        return extractFillBlankOptions(sourceText);
    }

    private static boolean matchesQuestionBlock(QuDetailDTO qu, String content, String block) {
        if (StringUtils.isBlank(block)) {
            return false;
        }
        String merged = StringUtils.defaultString(content);
        if (StringUtils.isBlank(merged)) {
            merged = StringUtils.defaultString(qu.getContent());
        }
        String stem = extractStemText(merged);
        if (StringUtils.isNotBlank(stem) && stem.length() >= 4) {
            String probe = stem.substring(0, Math.min(stem.length(), 16));
            if (block.contains(probe)) {
                return true;
            }
        }
        String signature = extractCodeSignature(merged);
        return StringUtils.isNotBlank(signature) && block.replaceAll("\\s+", " ").contains(signature);
    }

    private static String extractStemText(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        String text = content.trim();
        int codeIdx = text.indexOf("#include");
        if (codeIdx < 0) {
            codeIdx = text.indexOf("void main");
        }
        if (codeIdx < 0) {
            codeIdx = text.indexOf("int main");
        }
        if (codeIdx > 0) {
            text = text.substring(0, codeIdx).trim();
        }
        return text.replaceAll("(?m)^\\s*\\d+[.．、]\\s*", "").trim();
    }

    private static String extractCodeSignature(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ");
        int idx = compact.indexOf("#include");
        if (idx < 0) {
            idx = compact.indexOf("void main");
        }
        if (idx < 0) {
            idx = compact.indexOf("int main");
        }
        if (idx < 0) {
            return "";
        }
        return compact.substring(idx, Math.min(idx + 24, compact.length())).trim();
    }

    public static String stripFillBlankOptionSection(String content) {
        if (StringUtils.isBlank(content)) {
            return content;
        }
        String[] lines = content.split("\\r?\\n", -1);
        int cutLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isFillBlankOptionLine(lines[i])) {
                cutLine = i;
                break;
            }
        }
        if (cutLine < 0) {
            return content;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cutLine; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString().trim();
    }

    public static void attachFillBlankOptions(List<QuAnswerDTO> answers,
                                              Map<Integer, Map<String, String>> blankOptions) {
        if (answers == null || answers.isEmpty() || blankOptions == null || blankOptions.isEmpty()) {
            return;
        }
        for (int i = 0; i < answers.size(); i++) {
            QuAnswerDTO answer = answers.get(i);
            if (answer == null) {
                continue;
            }
            Map<String, String> opts = blankOptions.get(i + 1);
            if (opts == null || opts.size() < 2) {
                continue;
            }
            if (answer.getOptionList() != null && answer.getOptionList().size() >= 2) {
                continue;
            }
            answer.setOptionList(buildOptionList(answer.getContent(), opts));
        }
        fillOptionAnalysis(answers);
    }

    /**
     * 各空共用同一组候选项时，为尚未挂载 optionList 的空位补全选项。
     */
    public static void propagateSharedBlankOptions(List<QuAnswerDTO> answers,
                                                   Map<Integer, Map<String, String>> blankOptions) {
        if (answers == null || answers.isEmpty() || blankOptions == null || blankOptions.isEmpty()) {
            return;
        }
        Map<String, String> template = null;
        for (Map<String, String> opts : blankOptions.values()) {
            if (opts != null && opts.size() >= 2) {
                template = opts;
                break;
            }
        }
        if (template == null) {
            return;
        }
        for (int i = 0; i < answers.size(); i++) {
            QuAnswerDTO answer = answers.get(i);
            if (answer == null) {
                continue;
            }
            if (answer.getOptionList() != null && answer.getOptionList().size() >= 2) {
                continue;
            }
            Map<String, String> opts = blankOptions.get(i + 1);
            if (opts == null || opts.size() < 2) {
                opts = template;
            }
            answer.setOptionList(buildOptionList(answer.getContent(), opts));
        }
        fillOptionAnalysis(answers);
    }

    public static void fillOptionAnalysis(List<QuAnswerDTO> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        for (QuAnswerDTO answer : answers) {
            if (answer == null || answer.getOptionList() == null || answer.getOptionList().isEmpty()) {
                continue;
            }
            for (QuAnswerOptionDTO option : answer.getOptionList()) {
                if (option == null) {
                    continue;
                }
                if (StringUtils.isBlank(option.getAnalysis())) {
                    option.setAnalysis(Boolean.TRUE.equals(option.getIsRight())
                            ? "该选项为正确答案，符合题干要求。"
                            : "该选项不是正确答案，不符合题干要求。");
                }
            }
        }
    }

    public static void preserveAiOptionLists(List<QuAnswerDTO> targetAnswers, List<QuAnswerDTO> sourceAnswers) {
        if (targetAnswers == null || sourceAnswers == null || sourceAnswers.isEmpty()) {
            return;
        }
        for (int i = 0; i < targetAnswers.size() && i < sourceAnswers.size(); i++) {
            QuAnswerDTO source = sourceAnswers.get(i);
            QuAnswerDTO target = targetAnswers.get(i);
            if (source == null || target == null || source.getOptionList() == null || source.getOptionList().size() < 2) {
                continue;
            }
            target.setOptionList(copyOptionList(source.getOptionList(), target.getContent()));
        }
    }

    private static List<QuAnswerOptionDTO> copyOptionList(List<QuAnswerOptionDTO> sourceOptions, String correctContent) {
        List<QuAnswerOptionDTO> optionList = new ArrayList<>();
        for (QuAnswerOptionDTO source : sourceOptions) {
            if (source == null) {
                continue;
            }
            QuAnswerOptionDTO option = new QuAnswerOptionDTO();
            option.setLetter(source.getLetter());
            option.setContent(source.getContent());
            option.setAnalysis(source.getAnalysis());
            if (source.getIsRight() != null) {
                option.setIsRight(source.getIsRight());
            } else {
                option.setIsRight(isOptionCorrect(correctContent, source.getLetter(), source.getContent()));
            }
            optionList.add(option);
        }
        optionList.sort(Comparator.comparing(QuAnswerOptionDTO::getLetter));
        fillOptionItemAnalysis(optionList);
        return optionList;
    }

    private static void fillOptionItemAnalysis(List<QuAnswerOptionDTO> optionList) {
        for (QuAnswerOptionDTO option : optionList) {
            if (option == null || StringUtils.isNotBlank(option.getAnalysis())) {
                continue;
            }
            option.setAnalysis(Boolean.TRUE.equals(option.getIsRight())
                    ? "该选项为正确答案，符合题干要求。"
                    : "该选项不是正确答案，不符合题干要求。");
        }
    }

    public static void resolveFillBlankLetterAnswers(List<QuAnswerDTO> answers,
                                                     Map<Integer, Map<String, String>> blankOptions) {
        if (answers == null || answers.isEmpty() || blankOptions == null || blankOptions.isEmpty()) {
            return;
        }
        for (int i = 0; i < answers.size(); i++) {
            QuAnswerDTO answer = answers.get(i);
            if (answer == null || StringUtils.isBlank(answer.getContent())) {
                continue;
            }
            String text = answer.getContent().trim();
            Map<String, String> opts = blankOptions.get(i + 1);
            if (opts == null || opts.isEmpty()) {
                continue;
            }
            if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
                String letter = text.toUpperCase();
                if (opts.containsKey(letter)) {
                    answer.setContent(opts.get(letter));
                }
            }
        }
    }

    public static void encodeToRemark(QuDetailDTO qu) {
        if (qu == null || qu.getAnswerList() == null || qu.getAnswerList().isEmpty()) {
            return;
        }
        Map<String, List<QuAnswerOptionDTO>> payload = new LinkedHashMap<>();
        for (int i = 0; i < qu.getAnswerList().size(); i++) {
            QuAnswerDTO answer = qu.getAnswerList().get(i);
            if (answer == null || answer.getOptionList() == null || answer.getOptionList().size() < 2) {
                continue;
            }
            payload.put(String.valueOf(i + 1), answer.getOptionList());
        }
        if (payload.isEmpty()) {
            return;
        }
        qu.setRemark(REMARK_PREFIX + JSON.toJSONString(payload));
    }

    public static void decodeFromRemark(QuDetailDTO qu) {
        if (qu == null || StringUtils.isBlank(qu.getRemark()) || !qu.getRemark().startsWith(REMARK_PREFIX)) {
            return;
        }
        if (qu.getAnswerList() == null || qu.getAnswerList().isEmpty()) {
            return;
        }
        String json = qu.getRemark().substring(REMARK_PREFIX.length());
        Map<String, List<QuAnswerOptionDTO>> payload = JSON.parseObject(json,
                new TypeReference<Map<String, List<QuAnswerOptionDTO>>>() {
                });
        if (payload == null || payload.isEmpty()) {
            return;
        }
        for (int i = 0; i < qu.getAnswerList().size(); i++) {
            List<QuAnswerOptionDTO> optionList = payload.get(String.valueOf(i + 1));
            if (optionList != null && optionList.size() >= 2) {
                qu.getAnswerList().get(i).setOptionList(optionList);
            }
        }
        fillOptionAnalysis(qu.getAnswerList());
        qu.setRemark("");
    }

    private static List<QuAnswerOptionDTO> buildOptionList(String correctContent,
                                                           Map<String, String> opts) {
        List<QuAnswerOptionDTO> optionList = new ArrayList<>();
        for (Map.Entry<String, String> entry : opts.entrySet()) {
            QuAnswerOptionDTO option = new QuAnswerOptionDTO();
            option.setLetter(entry.getKey());
            option.setContent(entry.getValue());
            option.setIsRight(isOptionCorrect(correctContent, entry.getKey(), entry.getValue()));
            optionList.add(option);
        }
        optionList.sort(Comparator.comparing(QuAnswerOptionDTO::getLetter));
        fillOptionItemAnalysis(optionList);
        return optionList;
    }

    private static void parseInlineOptions(String text, Map<String, String> opts) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        Matcher matcher = FILL_OPTION_ITEM.matcher(text.trim());
        while (matcher.find()) {
            opts.put(matcher.group(1), matcher.group(2).trim());
        }
    }

    private static boolean isFillBlankOptionLine(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        return FILL_BLANK_OPTION_LINE.matcher(line.trim()).matches()
                && FILL_OPTION_ITEM.matcher(line.trim()).find();
    }

    private static boolean isOptionCorrect(String correctContent, String letter, String optionContent) {
        if (StringUtils.isBlank(correctContent)) {
            return false;
        }
        String trimmed = correctContent.trim();
        if (trimmed.length() == 1 && letter.equalsIgnoreCase(trimmed)) {
            return true;
        }
        return StringUtils.equals(normalizeOptionText(trimmed), normalizeOptionText(optionContent));
    }

    private static String normalizeOptionText(String text) {
        return StringUtils.defaultString(text)
                .replaceAll("^\\s*[A-Da-d]\\s*[\\.、．]\\s*", "")
                .replaceAll("\\s+", "")
                .trim();
    }
}
