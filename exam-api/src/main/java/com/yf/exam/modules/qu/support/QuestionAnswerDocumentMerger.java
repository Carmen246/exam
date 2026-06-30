package com.yf.exam.modules.qu.support;

import com.yf.exam.modules.qu.dto.AnswerDocumentMergeResultDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将答案文档按题号合并进试卷文本（在 AI 解析之前）
 */
@Component
public class QuestionAnswerDocumentMerger {

    private static final Pattern QUESTION_NO_PATTERN = Pattern.compile("(?m)^\\s*(\\d+)\\s*[\\.．、]\\s*");
    private static final Pattern QUESTION_SPLIT_PATTERN = Pattern.compile("(?m)(?=^\\s*\\d+[\\.．、]\\s*)");
    private static final Pattern FILL_BLANK_LINE = Pattern.compile("(?m)^\\s*第\\s*(\\d+)\\s*空\\s*[:：]?\\s*(.+)$");
    private static final Pattern NUMBERED_ANSWER_LINE = Pattern.compile(
            "(?m)^\\s*(?:答案\\s*)?(\\d+)\\s*[\\.．、\\)]\\s*(.+)$");
    private static final Pattern PAREN_NUMBER_ANSWER = Pattern.compile("(?m)^\\s*[（(]\\s*(\\d+)\\s*[）)]\\s*(.+)$");
    private static final Pattern ANSWER_LABEL_LINE = Pattern.compile("(?m)^\\s*答案\\s*[:：]\\s*(.+)$");

    public AnswerDocumentMergeResultDTO merge(String questionText, String answerText) {
        AnswerDocumentMergeResultDTO result = new AnswerDocumentMergeResultDTO();
        if (StringUtils.isBlank(questionText)) {
            result.setMergedText("");
            return result;
        }
        if (StringUtils.isBlank(answerText)) {
            result.setMergedText(questionText.trim());
            return result;
        }

        String normalizedQuestion = questionText.replace("\r\n", "\n").replace("\r", "\n").trim();
        String normalizedAnswer = answerText.replace("\r\n", "\n").replace("\r", "\n").trim();

        Map<Integer, List<String>> answerMap = parseAnswerDocument(normalizedAnswer);
        Set<Integer> questionNos = findQuestionNumbers(normalizedQuestion);
        result.getWarnings().addAll(buildWarnings(normalizedQuestion, questionNos, answerMap));
        result.setMergedText(appendAnswersToQuestions(normalizedQuestion, answerMap));
        return result;
    }

    public Map<Integer, List<String>> parseAnswerDocument(String answerText) {
        Map<Integer, List<String>> answerMap = new LinkedHashMap<>();
        if (StringUtils.isBlank(answerText)) {
            return answerMap;
        }

        Matcher matcher = QUESTION_NO_PATTERN.matcher(answerText);
        List<int[]> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(new int[] { matcher.start(), matcher.end(), Integer.parseInt(matcher.group(1)) });
        }

        if (blocks.isEmpty()) {
            return answerMap;
        }

        for (int i = 0; i < blocks.size(); i++) {
            int bodyStart = blocks.get(i)[1];
            int bodyEnd = i + 1 < blocks.size() ? blocks.get(i + 1)[0] : answerText.length();
            int questionNo = blocks.get(i)[2];
            String body = answerText.substring(bodyStart, bodyEnd).trim();
            List<String> answers = parseAnswerBlock(body);
            if (!answers.isEmpty()) {
                answerMap.put(questionNo, answers);
            }
        }
        return answerMap;
    }

    private List<String> parseAnswerBlock(String body) {
        List<String> answers = new ArrayList<>();
        if (StringUtils.isBlank(body)) {
            return answers;
        }

        TreeMap<Integer, String> blankAnswers = new TreeMap<>();
        Matcher blankMatcher = FILL_BLANK_LINE.matcher(body);
        while (blankMatcher.find()) {
            blankAnswers.put(Integer.parseInt(blankMatcher.group(1)), blankMatcher.group(2).trim());
        }
        if (!blankAnswers.isEmpty()) {
            answers.addAll(blankAnswers.values());
            return answers;
        }

        TreeMap<Integer, String> numberedAnswers = new TreeMap<>();
        collectNumberedAnswers(NUMBERED_ANSWER_LINE, body, numberedAnswers);
        collectNumberedAnswers(PAREN_NUMBER_ANSWER, body, numberedAnswers);
        if (!numberedAnswers.isEmpty()) {
            answers.addAll(numberedAnswers.values());
            return answers;
        }

        Matcher answerLabelMatcher = ANSWER_LABEL_LINE.matcher(body);
        if (answerLabelMatcher.find()) {
            answers.add(answerLabelMatcher.group(1).trim());
            return answers;
        }

        String[] lines = body.split("\n");
        for (String line : lines) {
            String value = line.trim();
            if (StringUtils.isBlank(value)) {
                continue;
            }
            if (value.matches("答案\\s*[:：].*")) {
                answers.add(value.replaceFirst("^答案\\s*[:：]\\s*", "").trim());
                return answers;
            }
        }

        String single = body.replaceAll("\n+", " ").trim();
        if (StringUtils.isNotBlank(single)) {
            answers.add(single);
        }
        return answers;
    }

    private void collectNumberedAnswers(Pattern pattern, String body, TreeMap<Integer, String> numberedAnswers) {
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            numberedAnswers.put(Integer.parseInt(matcher.group(1)), matcher.group(2).trim());
        }
    }

    private String appendAnswersToQuestions(String questionText, Map<Integer, List<String>> answerMap) {
        if (answerMap.isEmpty()) {
            return questionText;
        }

        String[] parts = QUESTION_SPLIT_PATTERN.split(questionText);
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            String block = part.trim();
            Integer questionNo = extractLeadingQuestionNo(block);
            sb.append(block);
            if (questionNo != null && answerMap.containsKey(questionNo)) {
                sb.append("\n\n").append(formatReferenceAnswers(answerMap.get(questionNo)));
            }
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    private String formatReferenceAnswers(List<String> answers) {
        if (answers == null || answers.isEmpty()) {
            return "";
        }
        if (answers.size() == 1) {
            return "参考答案：" + answers.get(0);
        }
        StringBuilder sb = new StringBuilder("参考答案：");
        for (int i = 0; i < answers.size(); i++) {
            sb.append("\n第").append(i + 1).append("空：").append(answers.get(i));
        }
        return sb.toString();
    }

    private List<String> buildWarnings(String questionText, Set<Integer> questionNos,
            Map<Integer, List<String>> answerMap) {
        List<String> warnings = new ArrayList<>();
        Set<Integer> answerNos = new LinkedHashSet<>(answerMap.keySet());

        for (Integer questionNo : questionNos) {
            if (!answerNos.contains(questionNo)) {
                warnings.add("试卷有第" + questionNo + "题，但答案文档未提供对应答案");
            }
        }
        for (Integer answerNo : answerNos) {
            if (!questionNos.contains(answerNo)) {
                warnings.add("答案文档有第" + answerNo + "题，但试卷中未找到对应题目");
            }
        }

        for (Integer questionNo : questionNos) {
            if (!answerMap.containsKey(questionNo)) {
                continue;
            }
            String block = extractQuestionBlock(questionText, questionNo);
            int blankCount = countBlanks(block);
            int answerCount = answerMap.get(questionNo).size();
            if (blankCount > 0 && blankCount != answerCount) {
                warnings.add("第" + questionNo + "题程序填空有 " + blankCount + " 个空，答案文档提供了 "
                        + answerCount + " 个空位答案");
            }
        }
        return warnings;
    }

    private Set<Integer> findQuestionNumbers(String questionText) {
        Set<Integer> numbers = new LinkedHashSet<>();
        Matcher matcher = QUESTION_NO_PATTERN.matcher(questionText);
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group(1)));
        }
        return numbers;
    }

    private Integer extractLeadingQuestionNo(String block) {
        Matcher matcher = Pattern.compile("^\\s*(\\d+)\\s*[\\.．、]").matcher(block);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private String extractQuestionBlock(String questionText, int questionNo) {
        Matcher matcher = QUESTION_NO_PATTERN.matcher(questionText);
        int start = -1;
        int end = questionText.length();
        while (matcher.find()) {
            int currentNo = Integer.parseInt(matcher.group(1));
            if (currentNo == questionNo) {
                start = matcher.start();
            } else if (start >= 0 && currentNo > questionNo) {
                end = matcher.start();
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        return questionText.substring(start, end);
    }

    private int countBlanks(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int idx = text.indexOf(FillProgramBlankProcessor.BLANK, from);
            if (idx < 0) {
                break;
            }
            count++;
            from = idx + FillProgramBlankProcessor.BLANK.length();
        }
        if (count > 0) {
            return count;
        }
        Matcher fillMatcher = Pattern.compile("\\{FILL:[^}]*}").matcher(text);
        while (fillMatcher.find()) {
            count++;
        }
        return count;
    }
}
