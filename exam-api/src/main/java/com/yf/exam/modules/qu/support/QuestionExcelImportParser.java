package com.yf.exam.modules.qu.support;

import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.dto.QuAnswerDTO;
import com.yf.exam.modules.qu.dto.export.QuExportDTO;
import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import com.yf.exam.modules.qu.enums.QuType;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用 Excel 题库导入解析器。
 * 支持系统导出模板（一行一个选项）与 WTS 等按行模板（一行一题、选项分列）。
 */
@Component
public class QuestionExcelImportParser {

    private static final Pattern OPTION_HEADER = Pattern.compile("^(?:选项)?([A-Fa-f])$");
    private static final Pattern BLANK_HEADER = Pattern.compile("^空([1-9]\\d*)$");
    private static final Pattern KEYWORD_HEADER = Pattern.compile("^(?:答案)?关键词([1-9]\\d*)$");

    public boolean isExcelFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        String ext = FilenameUtils.getExtension(fileName);
        return "xls".equalsIgnoreCase(ext) || "xlsx".equalsIgnoreCase(ext);
    }

    public List<QuDetailDTO> parse(File file) {
        if (file == null || !file.isFile()) {
            throw new ServiceException("Excel 文件不存在");
        }
        if (!isExcelFile(file.getName())) {
            throw new ServiceException("不是 Excel 文件");
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            return parse(inputStream, file.getName());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Excel 解析失败：" + e.getMessage());
        }
    }

    public List<QuDetailDTO> parse(InputStream inputStream, String fileName) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<QuDetailDTO> all = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet == null || isInstructionSheet(sheet)) {
                    continue;
                }
                all.addAll(parseSheet(sheet));
            }
            if (all.isEmpty()) {
                throw new ServiceException("Excel 中未找到有效试题数据，请确认表头包含「题目内容/题目描述」或「题目序号+选项内容」");
            }
            return all;
        }
    }

    public String buildPreviewText(List<QuDetailDTO> questions, String fileName) {
        Map<Integer, Integer> typeCount = new LinkedHashMap<>();
        for (QuDetailDTO qu : questions) {
            Integer type = qu.getQuType() == null ? 0 : qu.getQuType();
            typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Excel 文件：").append(StringUtils.defaultString(fileName)).append('\n');
        sb.append("共解析 ").append(questions.size()).append(" 题");
        if (!typeCount.isEmpty()) {
            sb.append("（");
            boolean first = true;
            for (Map.Entry<Integer, Integer> entry : typeCount.entrySet()) {
                if (!first) {
                    sb.append("，");
                }
                sb.append(typeLabel(entry.getKey())).append(' ').append(entry.getValue()).append(" 题");
                first = false;
            }
            sb.append('）');
        }
        return sb.toString();
    }

    private List<QuDetailDTO> parseSheet(Sheet sheet) {
        int headerRowIndex = findHeaderRowIndex(sheet);
        if (headerRowIndex < 0) {
            return Collections.emptyList();
        }

        Row headerRow = sheet.getRow(headerRowIndex);
        SheetHeader header = SheetHeader.fromRow(headerRow);
        if (header.isSystemExport()) {
            return parseSystemExportSheet(sheet, headerRowIndex, header);
        }
        if (!header.hasQuestionContent()) {
            return Collections.emptyList();
        }
        return parseRowQuestionSheet(sheet, headerRowIndex, header);
    }

    private List<QuDetailDTO> parseSystemExportSheet(Sheet sheet, int headerRowIndex, SheetHeader header) {
        List<QuExportDTO> rows = new ArrayList<>();
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isEmptyRow(row)) {
                continue;
            }
            String no = cellString(row, header.noCol);
            if (StringUtils.isBlank(no)) {
                continue;
            }
            QuExportDTO item = new QuExportDTO();
            item.setNo(no);
            item.setQuType(cellString(row, header.typeCol));
            item.setQContent(cellString(row, header.contentCol));
            item.setQAnalysis(cellString(row, header.analysisCol));
            item.setQImage(cellString(row, header.imageCol));
            item.setQVideo(cellString(row, header.videoCol));
            item.setRepoList(parseRepoList(cellString(row, header.repoCol)));
            item.setAIsRight(cellString(row, header.isRightCol));
            item.setAContent(cellString(row, header.optionContentCol));
            item.setAAnalysis(cellString(row, header.optionAnalysisCol));
            item.setAImage(cellString(row, header.optionImageCol));
            rows.add(item);
        }
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return convertExportRows(rows);
    }

    private List<String> parseRepoList(String raw) {
        if (StringUtils.isBlank(raw)) {
            return new ArrayList<>();
        }
        String[] parts = raw.split("[,，;；]");
        List<String> repos = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                repos.add(part.trim());
            }
        }
        return repos;
    }

    private List<QuDetailDTO> convertExportRows(List<QuExportDTO> dtoList) {
        Map<Integer, List<QuExportDTO>> answerMap = new HashMap<>(16);
        Map<Integer, QuExportDTO> questionMap = new LinkedHashMap<>(16);

        for (QuExportDTO item : dtoList) {
            if (StringUtils.isBlank(item.getNo())) {
                continue;
            }
            int key;
            try {
                key = Integer.parseInt(item.getNo().trim());
            } catch (Exception e) {
                continue;
            }
            answerMap.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            questionMap.putIfAbsent(key, item);
        }

        List<QuDetailDTO> result = new ArrayList<>();
        for (Map.Entry<Integer, QuExportDTO> entry : questionMap.entrySet()) {
            QuExportDTO im = entry.getValue();
            QuDetailDTO qu = new QuDetailDTO();
            qu.setContent(StringUtils.trimToEmpty(im.getQContent()));
            qu.setAnalysis(StringUtils.trimToEmpty(im.getQAnalysis()));
            qu.setImage(StringUtils.trimToEmpty(im.getQImage()));
            qu.setQuType(parseNumericType(im.getQuType()));
            qu.setRepoIds(im.getRepoList() == null ? new ArrayList<>() : new ArrayList<>(im.getRepoList()));
            qu.setAnswerList(buildAnswersFromExportRows(answerMap.get(entry.getKey())));
            result.add(qu);
        }
        return result;
    }

    private List<QuAnswerDTO> buildAnswersFromExportRows(List<QuExportDTO> rows) {
        List<QuAnswerDTO> answers = new ArrayList<>();
        if (rows == null) {
            return answers;
        }
        for (QuExportDTO row : rows) {
            if (StringUtils.isBlank(row.getAContent())) {
                continue;
            }
            QuAnswerDTO answer = new QuAnswerDTO();
            answer.setId("");
            answer.setContent(StringUtils.trimToEmpty(row.getAContent()));
            answer.setAnalysis(StringUtils.trimToEmpty(row.getAAnalysis()));
            answer.setIsRight("1".equals(StringUtils.trimToEmpty(row.getAIsRight())));
            answers.add(answer);
        }
        return answers;
    }

    private List<QuDetailDTO> parseRowQuestionSheet(Sheet sheet, int headerRowIndex, SheetHeader header) {
        List<QuDetailDTO> result = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        for (int rowIndex = headerRowIndex + 1; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isEmptyRow(row)) {
                continue;
            }
            QuDetailDTO qu = buildRowQuestion(row, header);
            if (qu != null) {
                result.add(qu);
            }
        }
        return result;
    }

    private QuDetailDTO buildRowQuestion(Row row, SheetHeader header) {
        String content = cellString(row, header.contentCol);
        if (StringUtils.isBlank(content)) {
            return null;
        }

        String typeText = header.typeCol >= 0 ? cellString(row, header.typeCol) : "";
        Integer quType = resolveQuType(typeText, header);
        String answerText = header.answerCol >= 0 ? cellString(row, header.answerCol) : "";
        String analysis = header.analysisCol >= 0 ? cellString(row, header.analysisCol) : "";
        String knowledge = header.knowledgeCol >= 0 ? cellString(row, header.knowledgeCol) : "";

        QuDetailDTO qu = new QuDetailDTO();
        qu.setContent(content.trim());
        qu.setAnalysis(StringUtils.trimToEmpty(analysis));
        qu.setQuType(quType);
        if (StringUtils.isNotBlank(knowledge)) {
            qu.setRemark("知识点：" + knowledge.trim());
        }

        if (QuType.JUDGE.equals(quType)) {
            qu.setAnswerList(buildJudgeAnswers(answerText));
        } else if (QuType.isFillType(quType) || isKeywordSheet(header)) {
            qu.setAnswerList(buildBlankOrKeywordAnswers(row, header, answerText));
        } else if (QuType.COMPREHENSIVE.equals(quType)) {
            qu.setAnswerList(buildComprehensiveAnswers(row, header, analysis));
        } else if (!header.optionCols.isEmpty()) {
            qu.setAnswerList(buildOptionAnswers(row, header, answerText, quType));
        } else if (StringUtils.isNotBlank(answerText)) {
            qu.setAnswerList(buildTextAnswers(answerText));
        } else {
            qu.setAnswerList(new ArrayList<>());
        }
        return qu;
    }

    private List<QuAnswerDTO> buildOptionAnswers(Row row, SheetHeader header, String answerText, Integer quType) {
        Set<Character> correctLetters = parseAnswerLetters(answerText);
        List<QuAnswerDTO> answers = new ArrayList<>();
        for (Map.Entry<Character, Integer> entry : header.optionCols.entrySet()) {
            String optionContent = cellString(row, entry.getValue());
            if (StringUtils.isBlank(optionContent)) {
                continue;
            }
            QuAnswerDTO answer = new QuAnswerDTO();
            answer.setId("");
            answer.setContent(optionContent.trim());
            answer.setIsRight(correctLetters.contains(entry.getKey()));
            answers.add(answer);
        }
        if (answers.isEmpty() && StringUtils.isNotBlank(answerText)) {
            return buildTextAnswers(answerText);
        }
        if (QuType.RADIO.equals(quType)) {
            int trueCount = 0;
            for (QuAnswerDTO answer : answers) {
                if (Boolean.TRUE.equals(answer.getIsRight())) {
                    trueCount++;
                }
            }
            if (trueCount == 0) {
                markSingleLetterAnswer(answers, answerText);
            }
        }
        return answers;
    }

    private void markSingleLetterAnswer(List<QuAnswerDTO> answers, String answerText) {
        Set<Character> letters = parseAnswerLetters(answerText);
        if (letters.size() != 1) {
            return;
        }
        char letter = letters.iterator().next();
        int index = letter - 'A';
        if (index >= 0 && index < answers.size()) {
            for (int i = 0; i < answers.size(); i++) {
                answers.get(i).setIsRight(i == index);
            }
        }
    }

    private List<QuAnswerDTO> buildJudgeAnswers(String answerText) {
        QuAnswerDTO right = new QuAnswerDTO();
        right.setId("");
        right.setContent("正确");

        QuAnswerDTO wrong = new QuAnswerDTO();
        wrong.setId("");
        wrong.setContent("错误");

        if (isTrueAnswer(answerText)) {
            right.setIsRight(true);
            wrong.setIsRight(false);
        } else if (isFalseAnswer(answerText)) {
            right.setIsRight(false);
            wrong.setIsRight(true);
        } else {
            Set<Character> letters = parseAnswerLetters(answerText);
            if (letters.contains('A')) {
                right.setIsRight(true);
                wrong.setIsRight(false);
            } else if (letters.contains('B')) {
                right.setIsRight(false);
                wrong.setIsRight(true);
            } else {
                right.setIsRight(true);
                wrong.setIsRight(false);
            }
        }

        return Arrays.asList(right, wrong);
    }

    private List<QuAnswerDTO> buildBlankOrKeywordAnswers(Row row, SheetHeader header, String answerText) {
        List<QuAnswerDTO> answers = new ArrayList<>();
        if (!header.blankCols.isEmpty()) {
            for (Integer col : header.blankCols.values()) {
                appendTextAnswer(answers, cellString(row, col));
            }
        }
        if (!header.keywordCols.isEmpty()) {
            for (Integer col : header.keywordCols.values()) {
                appendTextAnswer(answers, cellString(row, col));
            }
        }
        if (answers.isEmpty() && StringUtils.isNotBlank(answerText)) {
            appendTextAnswer(answers, answerText);
        }
        for (QuAnswerDTO answer : answers) {
            answer.setIsRight(Boolean.TRUE);
        }
        return answers;
    }

    private List<QuAnswerDTO> buildComprehensiveAnswers(Row row, SheetHeader header, String analysis) {
        List<QuAnswerDTO> answers = new ArrayList<>();
        if (header.rubricCol >= 0) {
            appendTextAnswer(answers, cellString(row, header.rubricCol));
        }
        if (answers.isEmpty() && StringUtils.isNotBlank(analysis)) {
            appendTextAnswer(answers, analysis);
        }
        for (QuAnswerDTO answer : answers) {
            answer.setIsRight(Boolean.TRUE);
        }
        return answers;
    }

    private List<QuAnswerDTO> buildTextAnswers(String answerText) {
        List<QuAnswerDTO> answers = new ArrayList<>();
        appendTextAnswer(answers, answerText);
        for (QuAnswerDTO answer : answers) {
            answer.setIsRight(Boolean.TRUE);
        }
        return answers;
    }

    private void appendTextAnswer(List<QuAnswerDTO> answers, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        QuAnswerDTO answer = new QuAnswerDTO();
        answer.setId("");
        answer.setContent(text.trim());
        answer.setIsRight(Boolean.TRUE);
        answers.add(answer);
    }

    private Integer resolveQuType(String typeText, SheetHeader header) {
        Integer mapped = mapTypeText(typeText);
        if (mapped != null) {
            return mapped;
        }
        if (!header.optionCols.isEmpty()) {
            return QuType.RADIO;
        }
        if (!header.blankCols.isEmpty()) {
            return QuType.FILL;
        }
        if (!header.keywordCols.isEmpty()) {
            return QuType.FILL;
        }
        if (header.rubricCol >= 0) {
            return QuType.COMPREHENSIVE;
        }
        if (header.answerCol >= 0 && header.optionCols.isEmpty() && header.blankCols.isEmpty()) {
            return QuType.JUDGE;
        }
        return QuType.FILL;
    }

    private Integer mapTypeText(String typeText) {
        if (StringUtils.isBlank(typeText)) {
            return null;
        }
        String text = normalizeHeader(typeText);
        if (text.contains("单选")) {
            return QuType.RADIO;
        }
        if (text.contains("多选")) {
            return QuType.MULTI;
        }
        if (text.contains("判断")) {
            return QuType.JUDGE;
        }
        if (text.contains("程序填空")) {
            return QuType.FILL_PROGRAM;
        }
        if (text.contains("阅读程序") || text.contains("程序阅读") || text.contains("写结果")) {
            return QuType.READ_PROGRAM;
        }
        if (text.contains("改错")) {
            return QuType.FIX_PROGRAM;
        }
        if (text.contains("编程")) {
            return QuType.PROGRAM;
        }
        if (text.contains("综合") || text.contains("附加")) {
            return QuType.COMPREHENSIVE;
        }
        if (text.contains("填空")) {
            return QuType.FILL;
        }
        if (text.contains("问答") || text.contains("简答") || text.contains("主观")) {
            return QuType.FILL;
        }
        Integer numeric = parseNumericType(typeText);
        if (numeric != null && numeric >= 1 && numeric <= 9) {
            return numeric;
        }
        return null;
    }

    private Integer parseNumericType(String typeText) {
        if (StringUtils.isBlank(typeText)) {
            return null;
        }
        String trimmed = typeText.trim();
        if (trimmed.matches("\\d+(\\.0)?")) {
            trimmed = trimmed.replace(".0", "");
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<Character> parseAnswerLetters(String answerText) {
        Set<Character> letters = new HashSet<>();
        if (StringUtils.isBlank(answerText)) {
            return letters;
        }
        Matcher matcher = Pattern.compile("[A-Fa-f]").matcher(answerText);
        while (matcher.find()) {
            letters.add(Character.toUpperCase(matcher.group().charAt(0)));
        }
        return letters;
    }

    private boolean isTrueAnswer(String answerText) {
        String text = StringUtils.trimToEmpty(answerText);
        return "对".equals(text) || "正确".equals(text) || "是".equals(text) || "T".equalsIgnoreCase(text)
                || "TRUE".equalsIgnoreCase(text) || "1".equals(text) || "√".equals(text);
    }

    private boolean isFalseAnswer(String answerText) {
        String text = StringUtils.trimToEmpty(answerText);
        return "错".equals(text) || "错误".equals(text) || "否".equals(text) || "F".equalsIgnoreCase(text)
                || "FALSE".equalsIgnoreCase(text) || "0".equals(text) || "×".equals(text) || "x".equalsIgnoreCase(text);
    }

    private boolean isKeywordSheet(SheetHeader header) {
        return !header.keywordCols.isEmpty();
    }

    private int findHeaderRowIndex(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), 5);
        for (int i = 0; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            SheetHeader header = SheetHeader.fromRow(row);
            if (header.isSystemExport() || header.hasQuestionContent()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInstructionSheet(Sheet sheet) {
        String sheetName = StringUtils.defaultString(sheet.getSheetName()).trim();
        if (sheetName.contains("说明") || sheetName.equalsIgnoreCase("readme")) {
            return true;
        }
        return findHeaderRowIndex(sheet) < 0;
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        short first = row.getFirstCellNum();
        short last = row.getLastCellNum();
        if (first < 0 || last <= first) {
            return true;
        }
        for (int i = first; i < last; i++) {
            if (StringUtils.isNotBlank(cellString(row, i))) {
                return false;
            }
        }
        return true;
    }

    private String cellString(Row row, int column) {
        if (row == null || column < 0) {
            return "";
        }
        Object value = getCellValue(row, column);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Object getCellValue(Row row, int column) {
        Object val = "";
        try {
            Cell cell = row.getCell(column);
            if (cell == null) {
                return val;
            }
            CellType cellType = cell.getCellType();
            if (cellType == CellType.FORMULA) {
                cellType = cell.getCachedFormulaResultType();
            }
            if (cellType == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    NumberFormat nf = NumberFormat.getInstance();
                    nf.setGroupingUsed(false);
                    val = nf.format(cell.getNumericCellValue());
                }
            } else if (cellType == CellType.STRING) {
                val = cell.getStringCellValue();
            } else if (cellType == CellType.BOOLEAN) {
                val = cell.getBooleanCellValue();
            } else if (cellType == CellType.ERROR) {
                val = cell.getErrorCellValue();
            }
        } catch (Exception ignored) {
            return val;
        }
        if (val instanceof String && ((String) val).endsWith(".0")) {
            return StringUtils.substringBefore((String) val, ".0");
        }
        return val;
    }

    private String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String typeLabel(Integer quType) {
        if (QuType.RADIO.equals(quType)) {
            return "单选题";
        }
        if (QuType.MULTI.equals(quType)) {
            return "多选题";
        }
        if (QuType.JUDGE.equals(quType)) {
            return "判断题";
        }
        if (QuType.FILL.equals(quType)) {
            return "填空题";
        }
        if (QuType.FILL_PROGRAM.equals(quType)) {
            return "程序填空题";
        }
        if (QuType.READ_PROGRAM.equals(quType)) {
            return "阅读程序题";
        }
        if (QuType.PROGRAM.equals(quType)) {
            return "编程题";
        }
        if (QuType.FIX_PROGRAM.equals(quType)) {
            return "程序改错题";
        }
        if (QuType.COMPREHENSIVE.equals(quType)) {
            return "综合应用题";
        }
        return "题型" + quType;
    }

    private static class SheetHeader {
        private int typeCol = -1;
        private int contentCol = -1;
        private int answerCol = -1;
        private int analysisCol = -1;
        private int knowledgeCol = -1;
        private int rubricCol = -1;
        private int noCol = -1;
        private int optionContentCol = -1;
        private int isRightCol = -1;
        private int imageCol = -1;
        private int videoCol = -1;
        private int repoCol = -1;
        private int optionAnalysisCol = -1;
        private int optionImageCol = -1;
        private final Map<Character, Integer> optionCols = new LinkedHashMap<>();
        private final Map<Integer, Integer> blankCols = new LinkedHashMap<>();
        private final Map<Integer, Integer> keywordCols = new LinkedHashMap<>();

        static SheetHeader fromRow(Row row) {
            SheetHeader header = new SheetHeader();
            if (row == null) {
                return header;
            }
            short last = row.getLastCellNum();
            for (int c = 0; c < last; c++) {
                String raw = readHeaderCell(row, c);
                if (StringUtils.isBlank(raw)) {
                    continue;
                }
                String normalized = normalizeStatic(raw);
                header.applyHeader(normalized, c);
            }
            return header;
        }

        private void applyHeader(String normalized, int col) {
            if (matches(normalized, "题目序号", "序号", "no")) {
                noCol = col;
                return;
            }
            if (matches(normalized, "题目类型", "题型", "qutype")) {
                typeCol = col;
                return;
            }
            if (matches(normalized, "题目内容", "题目描述", "题目", "题干", "qcontent", "qdesc")) {
                contentCol = col;
                return;
            }
            if (matches(normalized, "答案", "答", "正确答案")) {
                answerCol = col;
                return;
            }
            if (matches(normalized, "解析", "整体解析", "题目解析", "qanalysis")) {
                analysisCol = col;
                return;
            }
            if (matches(normalized, "知识点", "知识分类")) {
                knowledgeCol = col;
                return;
            }
            if (matches(normalized, "评分标准", "评分要点")) {
                rubricCol = col;
                return;
            }
            if (matches(normalized, "题目图片", "qimage")) {
                imageCol = col;
                return;
            }
            if (matches(normalized, "题目视频", "qvideo")) {
                videoCol = col;
                return;
            }
            if (matches(normalized, "所属题库", "题库")) {
                repoCol = col;
                return;
            }
            if (matches(normalized, "选项内容", "acontent")) {
                optionContentCol = col;
                return;
            }
            if (matches(normalized, "选项解析", "aanalysis")) {
                optionAnalysisCol = col;
                return;
            }
            if (matches(normalized, "选项图片", "aimage")) {
                optionImageCol = col;
                return;
            }
            if (matches(normalized, "是否正确项", "是否正确", "aisright")) {
                isRightCol = col;
                return;
            }

            Matcher optionMatcher = OPTION_HEADER.matcher(normalized);
            if (optionMatcher.matches()) {
                optionCols.put(Character.toUpperCase(optionMatcher.group(1).charAt(0)), col);
                return;
            }
            if (normalized.startsWith("选项") && normalized.length() == 3) {
                char letter = Character.toUpperCase(normalized.charAt(2));
                if (letter >= 'A' && letter <= 'F') {
                    optionCols.put(letter, col);
                }
            }

            Matcher blankMatcher = BLANK_HEADER.matcher(normalized);
            if (blankMatcher.matches()) {
                blankCols.put(Integer.parseInt(blankMatcher.group(1)), col);
                return;
            }

            Matcher keywordMatcher = KEYWORD_HEADER.matcher(normalized);
            if (keywordMatcher.matches()) {
                keywordCols.put(Integer.parseInt(keywordMatcher.group(1)), col);
                return;
            }
            if (normalized.contains("关键词")) {
                keywordCols.put(keywordCols.size() + 1, col);
            }
        }

        boolean isSystemExport() {
            return noCol >= 0 && optionContentCol >= 0;
        }

        boolean hasQuestionContent() {
            return contentCol >= 0;
        }

        private static boolean matches(String normalized, String... aliases) {
            for (String alias : aliases) {
                if (normalized.equals(normalizeStatic(alias))) {
                    return true;
                }
            }
            return false;
        }

        private static String normalizeStatic(String raw) {
            if (raw == null) {
                return "";
            }
            return raw.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        }

        private static String readHeaderCell(Row row, int column) {
            Cell cell = row.getCell(column);
            if (cell == null) {
                return "";
            }
            cell.setCellType(CellType.STRING);
            return StringUtils.trimToEmpty(cell.getStringCellValue());
        }
    }
}
