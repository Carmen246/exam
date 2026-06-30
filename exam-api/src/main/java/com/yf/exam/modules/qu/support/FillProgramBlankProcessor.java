package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 程序填空题：从代码中识别空位、抽取答案，并将空位替换为 ____
 */
@Component
public class FillProgramBlankProcessor {

    public static final String BLANK = "____";
    public static final String FILL_PREFIX = "{FILL:";
    public static final String FILL_SUFFIX = "}";

    /** Word 多空格包围的填空片段，如 if(    max<a[row][col]    ) */
    private static final Pattern SPACE_PADDED_BLANK = Pattern.compile(
            "([(,])\\s{4,}([^\\s(,][^,)]*?)\\s{4,}([),;])");

    /** docx/预处理注入的 {FILL:答案} 标记 */
    private static final Pattern FILL_MARKER = Pattern.compile("\\{FILL:([^}]+)}");

    /** 已有 ____ 占位 */
    private static final Pattern EXISTING_BLANK = Pattern.compile("_{3,}");

    /** if/else if/while 条件中的潜在填空 */
    private static final Pattern CONDITION_BLANK = Pattern.compile(
            "(?:\\bif|\\belse\\s+if|\\bwhile)\\s*\\(\\s*([^)]+?)\\s*\\)");

    public static class BlankSlot {
        private final String content;
        private final String note;

        public BlankSlot(String content, String note) {
            this.content = content;
            this.note = note;
        }

        public String getContent() {
            return content;
        }

        public String getNote() {
            return note;
        }
    }

    public static class ProcessResult {
        private String code;
        private List<BlankSlot> blanks = new ArrayList<>();

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public List<BlankSlot> getBlanks() {
            return blanks;
        }

        public void setBlanks(List<BlankSlot> blanks) {
            this.blanks = blanks;
        }
    }

    /**
     * 在本地清洗前，把 Word 多空格包围的片段标记为 {FILL:答案}
     */
    public String markSpacePaddedBlanks(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        Matcher matcher = SPACE_PADDED_BLANK.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String answer = matcher.group(2).trim();
            if (StringUtils.isBlank(answer) || looksLikePlainText(answer)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String replacement = matcher.group(1) + FILL_PREFIX + answer + FILL_SUFFIX + matcher.group(3);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String restoreFillMarkers(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        Matcher matcher = FILL_MARKER.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1).trim()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private List<BlankSlot> detectConditionBlanks(String code) {
        List<BlankSlot> result = new ArrayList<>();
        if (StringUtils.isBlank(code)) {
            return result;
        }
        Matcher matcher = CONDITION_BLANK.matcher(code);
        while (matcher.find()) {
            String condition = matcher.group(1).trim();
            if (StringUtils.isBlank(condition) || condition.contains(BLANK)) {
                continue;
            }
            if (looksLikeCodeFragment(condition)) {
                result.add(new BlankSlot(condition, null));
            }
        }
        return result;
    }

    /**
     * 处理程序代码：抽取全部空位答案，并将代码中的答案替换为 ____
     */
    public ProcessResult process(String code, List<String> aiAnswers) {
        ProcessResult result = new ProcessResult();
        if (StringUtils.isBlank(code)) {
            result.setCode(code);
            return result;
        }

        String working = code;
        List<BlankSlot> extracted = new ArrayList<>();

        working = extractFillMarkers(working, extracted);
        int existingBlankCount = countExistingBlanks(working);
        working = extractSpacePaddedBlanks(working, extracted);

        if (extracted.isEmpty() && existingBlankCount == 0) {
            extracted.addAll(detectConditionBlanks(working));
        }

        List<BlankSlot> merged = mergeBlanks(extracted, aiAnswers, existingBlankCount);

        // 若已由 {FILL:...} 或空格空位生成 ____，不再全局 replace，避免 a[num]='\0' 被误替换成第三空
        if (countExistingBlanks(working) == 0) {
            working = replaceAnswersWithBlanks(working, merged);
        }
        working = normalizeBlankPlaceholder(working);

        int blankCount = countExistingBlanks(working);
        if (blankCount > 0 && blankCount != merged.size()) {
            merged = reconcileBlankCount(working, merged);
        }

        // 等价写法写在同一空的 content 里，不增加 answerList 条数
        merged = addEquivalentAnswers(merged);

        result.setCode(working);
        result.setBlanks(merged);
        return result;
    }

    private List<BlankSlot> addEquivalentAnswers(List<BlankSlot> blanks) {
        if (blanks == null || blanks.isEmpty()) {
            return blanks;
        }
        List<BlankSlot> result = new ArrayList<>();
        for (BlankSlot slot : blanks) {
            if (slot == null || StringUtils.isBlank(slot.getContent())) {
                result.add(slot);
                continue;
            }
            String content = slot.getContent().trim();
            String equivalent = buildCTruthExpressionEquivalent(content);
            if (StringUtils.isNotBlank(equivalent) && !containsAnswerVariant(content, equivalent)) {
                result.add(new BlankSlot(content + " / " + equivalent, slot.getNote()));
            } else {
                result.add(slot);
            }
        }
        return result;
    }

    private String buildCTruthExpressionEquivalent(String answer) {
        String normalized = normalizeExpression(answer);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        if (normalized.contains("!=") || normalized.contains("==")
                || normalized.contains(">") || normalized.contains("<")) {
            return null;
        }
        if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*(\\[[^\\]]+])+")) {
            return null;
        }
        return normalized + "!='\\0'";
    }

    private boolean containsAnswerVariant(String content, String variant) {
        String normalizedVariant = normalizeExpression(variant);
        String[] parts = content.split("[；;|/、]");
        for (String part : parts) {
            if (StringUtils.equals(normalizeExpression(part), normalizedVariant)) {
                return true;
            }
        }
        return false;
    }

    private String extractFillMarkers(String code, List<BlankSlot> extracted) {
        Matcher matcher = FILL_MARKER.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String answer = matcher.group(1).trim();
            extracted.add(new BlankSlot(answer, null));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(BLANK));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String extractSpacePaddedBlanks(String code, List<BlankSlot> extracted) {
        Matcher matcher = SPACE_PADDED_BLANK.matcher(code);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String answer = matcher.group(2).trim();
            if (StringUtils.isBlank(answer) || looksLikePlainText(answer)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            extracted.add(new BlankSlot(answer, null));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + " " + BLANK + " " + matcher.group(3)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private List<BlankSlot> mergeBlanks(List<BlankSlot> extracted, List<String> aiAnswers, int existingBlankCount) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        List<BlankSlot> result = new ArrayList<>();

        for (BlankSlot slot : extracted) {
            if (ordered.add(slot.getContent())) {
                result.add(slot);
            }
        }

        if (!result.isEmpty() && aiAnswers != null && !aiAnswers.isEmpty()) {
            result = alignWithAiAnswers(result, aiAnswers);
            return result;
        }

        if (result.isEmpty() && aiAnswers != null) {
            for (String ai : aiAnswers) {
                if (StringUtils.isBlank(ai)) {
                    continue;
                }
                String trimmed = ai.trim();
                if (ordered.add(trimmed)) {
                    result.add(new BlankSlot(trimmed, null));
                }
            }
        }

        if (existingBlankCount > result.size() && aiAnswers != null) {
            for (String ai : aiAnswers) {
                if (StringUtils.isBlank(ai)) {
                    continue;
                }
                String trimmed = ai.trim();
                if (ordered.add(trimmed)) {
                    result.add(new BlankSlot(trimmed, null));
                }
            }
        }

        return result;
    }

    private List<BlankSlot> alignWithAiAnswers(List<BlankSlot> extracted, List<String> aiAnswers) {
        List<BlankSlot> result = new ArrayList<>();
        boolean[] usedAi = new boolean[aiAnswers.size()];

        for (BlankSlot slot : extracted) {
            String content = slot.getContent();
            String note = slot.getNote();
            int matchedAi = findBestAiMatch(content, aiAnswers, usedAi);
            if (matchedAi >= 0) {
                String ai = aiAnswers.get(matchedAi).trim();
                usedAi[matchedAi] = true;
                if (!StringUtils.equals(content, ai)) {
                    note = buildCorrectionNote(content, ai);
                    content = ai;
                }
            }
            result.add(new BlankSlot(content, note));
        }

        for (int i = 0; i < aiAnswers.size(); i++) {
            if (!usedAi[i] && StringUtils.isNotBlank(aiAnswers.get(i))) {
                result.add(new BlankSlot(aiAnswers.get(i).trim(), null));
            }
        }
        return result;
    }

    private int findBestAiMatch(String original, List<String> aiAnswers, boolean[] usedAi) {
        int indexMatch = -1;
        int fuzzyMatch = -1;
        for (int i = 0; i < aiAnswers.size(); i++) {
            if (usedAi[i] || StringUtils.isBlank(aiAnswers.get(i))) {
                continue;
            }
            String ai = aiAnswers.get(i).trim();
            if (StringUtils.equals(original, ai)) {
                return i;
            }
            if (indexMatch < 0) {
                indexMatch = i;
            }
            if (fuzzyMatch < 0 && isLikelySameBlank(original, ai)) {
                fuzzyMatch = i;
            }
        }
        return fuzzyMatch >= 0 ? fuzzyMatch : indexMatch;
    }

    private String replaceAnswersWithBlanks(String code, List<BlankSlot> blanks) {
        if (StringUtils.isBlank(code) || blanks.isEmpty()) {
            return normalizeBlankPlaceholder(code);
        }

        List<Replacement> replacements = new ArrayList<>();
        String working = code;
        for (BlankSlot slot : blanks) {
            MatchRange range = findAnswerRange(working, primaryAnswer(slot.getContent()));
            if (range == null && StringUtils.isNotBlank(slot.getNote())) {
                String original = parseOriginalFromNote(slot.getNote());
                if (StringUtils.isNotBlank(original)) {
                    range = findAnswerRange(working, primaryAnswer(original));
                }
            }
            if (range != null) {
                replacements.add(new Replacement(range.start, range.end));
            }
        }

        replacements.sort(Comparator.comparingInt((Replacement r) -> r.start).reversed());
        for (Replacement replacement : replacements) {
            working = working.substring(0, replacement.start)
                    + BLANK
                    + working.substring(replacement.end);
        }

        return normalizeBlankPlaceholder(working);
    }

    private List<BlankSlot> reconcileBlankCount(String code, List<BlankSlot> blanks) {
        int blankCount = countExistingBlanks(code);
        if (blankCount <= blanks.size()) {
            return blanks;
        }
        List<BlankSlot> result = new ArrayList<>(blanks);
        while (result.size() < blankCount) {
            result.add(new BlankSlot("", null));
        }
        return result;
    }

    private String normalizeBlankPlaceholder(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }
        return code.replaceAll("_{3,}", BLANK);
    }

    private int countExistingBlanks(String code) {
        if (StringUtils.isBlank(code)) {
            return 0;
        }
        int count = 0;
        Matcher matcher = EXISTING_BLANK.matcher(code);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private MatchRange findAnswerRange(String text, String answer) {
        if (StringUtils.isBlank(text) || StringUtils.isBlank(answer)) {
            return null;
        }
        answer = primaryAnswer(answer);
        int exact = text.indexOf(answer);
        if (exact >= 0) {
            return new MatchRange(exact, exact + answer.length());
        }

        String flexible = buildFlexiblePattern(answer);
        Matcher matcher = Pattern.compile(flexible).matcher(text);
        if (matcher.find()) {
            return new MatchRange(matcher.start(), matcher.end());
        }
        return null;
    }

    private String primaryAnswer(String content) {
        if (StringUtils.isBlank(content)) {
            return content;
        }
        String[] parts = content.split("\\s+/\\s+|[；;|、]");
        return parts[0].trim();
    }

    private String buildFlexiblePattern(String answer) {
        String escaped = Pattern.quote(primaryAnswer(answer).trim());
        escaped = escaped.replace("\\ ", "\\s+");
        return escaped;
    }

    private boolean looksLikePlainText(String text) {
        if (StringUtils.isBlank(text)) {
            return true;
        }
        return text.matches("^[\\u4e00-\\u9fa5，。；：、！？\\s]+$");
    }

    private boolean looksLikeCodeFragment(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        return text.matches(".*[<>!=]=?.*")
                || text.matches(".*\\[[^\\]]+].*")
                || text.matches(".*\\w+\\s*\\(.*");
    }

    private boolean isLikelySameBlank(String original, String corrected) {
        if (StringUtils.isBlank(original) || StringUtils.isBlank(corrected)) {
            return false;
        }
        String a = original.replaceAll("\\s+", "");
        String b = corrected.replaceAll("\\s+", "");
        if (StringUtils.equals(a, b)) {
            return true;
        }
        return a.replace("<", ">").replace(">", "<").equals(b)
                || (a.contains("min") && b.contains("min") && a.contains("max") && b.contains("max"));
    }

    private String buildCorrectionNote(String original, String corrected) {
        return "原文可能为 " + original + "，按题意应为 " + corrected;
    }

    private String parseOriginalFromNote(String note) {
        if (StringUtils.isBlank(note)) {
            return null;
        }
        Matcher matcher = Pattern.compile("原文可能为\\s*(.+?)\\s*，按题意应为").matcher(note);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private static class MatchRange {
        private final int start;
        private final int end;

        private MatchRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class Replacement {
        private final int start;
        private final int end;

        private Replacement(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
