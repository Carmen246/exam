package com.yf.exam.modules.qu.support;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 答案文档表格解析器：解析使用√标记的表格网格格式的答案文档(.docx)。
 * 将表格形式的答案转换为 QuestionAnswerDocumentMerger 可消费的文本格式。
 */
public class AnswerDocumentTableParser {

    private static final String CHECK_MARK = "√";

    /**
     * 解析答案文档，返回结构化文本供合并器使用。
     * 各题型答案按全局连续编号：判断题1-N，单选题N+1-M，以此类推，
     * 避免不同题型的题号冲突（如判断题"1."和单选题"1."覆盖同一答案）。
     * 偏移量使用题型标题中"本大题共N小题"的N值，而非表格中的实际答案数，
     * 以正确处理程序填空题（4道大题但10个空位答案）等情况。
     * 如果未找到含√标记的表格，返回 null（调用方应回退到普通文本解析）。
     */
    public static String parse(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder result = new StringBuilder();
            String currentSection = null;
            int questionOffset = 0;          // 全局题号偏移量
            int headerQCount = 0;            // 从标题"本大题共N小题"提取的题数
            int tableMaxQ = 0;               // 表格中解码到的最大题号（fallback）

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    String text = getParagraphText((XWPFParagraph) element);
                    String section = detectSection(text);
                    if (section != null) {
                        // 遇到新题型时，将上一题型的题数加入偏移量
                        // 优先使用标题中的"本大题共N小题"，fallback到表格最大题号
                        questionOffset += (headerQCount > 0 ? headerQCount : tableMaxQ);
                        headerQCount = extractQuestionCount(text);
                        tableMaxQ = 0;
                        currentSection = section;
                        result.append("\n").append(text).append("\n");
                    }
                } else if (element instanceof XWPFTable) {
                    XWPFTable table = (XWPFTable) element;
                    if (hasCheckMarks(table) && currentSection != null) {
                        String decoded = decodeTable(table, currentSection);
                        if (StringUtils.isNotBlank(decoded)) {
                            // 更新表格最大题号（作为fallback）
                            int maxInTable = getMaxQuestionNumber(decoded);
                            tableMaxQ = Math.max(tableMaxQ, maxInTable);
                            // 按全局偏移量重新编号，避免跨题型题号冲突
                            String renumbered = renumberDecoded(decoded, questionOffset);
                            result.append(renumbered);
                        }
                    }
                }
            }

            String text = result.toString().trim();
            return StringUtils.isBlank(text) ? null : text;
        }
    }

    /**
     * 从题型标题中提取"本大题共N小题"的N值
     */
    private static int extractQuestionCount(String headerText) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("本大题共(\\d+)小题").matcher(headerText);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    /**
     * 从解码后的答案文本中提取最大题号
     */
    private static int getMaxQuestionNumber(String decoded) {
        int max = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\.", java.util.regex.Pattern.MULTILINE).matcher(decoded);
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max;
    }

    /**
     * 将解码后的答案文本按偏移量重新编号。
     * 例如 offset=10 时，"1. F\n2. T" → "11. F\n12. T"
     */
    private static String renumberDecoded(String decoded, int offset) {
        if (offset == 0) {
            return decoded;
        }
        StringBuffer sb = new StringBuffer();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\.", java.util.regex.Pattern.MULTILINE).matcher(decoded);
        while (m.find()) {
            int oldNum = Integer.parseInt(m.group(1));
            String replacement = (oldNum + offset) + ".";
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String getParagraphText(XWPFParagraph paragraph) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String t = run.getText(0);
            if (t != null) {
                sb.append(t);
            }
        }
        return sb.toString().trim();
    }

    private static String detectSection(String text) {
        if (text.contains("判断题") && text.contains("本大题共")) return "判断题";
        if (text.contains("单选题") && text.contains("本大题共")) return "单选题";
        if (text.contains("多选题") && text.contains("本大题共")) return "多选题";
        if (text.contains("程序填空题") && text.contains("本大题共")) return "程序填空题";
        if (text.contains("程序阅读题") && text.contains("本大题共")) return "程序阅读题";
        if (text.contains("程序设计题") && text.contains("本大题共")) return "程序设计题";
        return null;
    }

    private static boolean hasCheckMarks(XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    if (getParagraphText(p).contains(CHECK_MARK)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String decodeTable(XWPFTable table, String sectionType) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return "";

        // 将表格数据提取为字符串网格
        List<List<String>> grid = new ArrayList<>();
        for (XWPFTableRow row : rows) {
            List<String> rowData = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = "";
                for (XWPFParagraph p : cell.getParagraphs()) {
                    text += getParagraphText(p);
                }
                rowData.add(text.trim());
            }
            grid.add(rowData);
        }

        if (grid.size() < 2) return "";

        // 第一行为表头，包含题号
        List<String> header = grid.get(0);

        if ("判断题".equals(sectionType)) {
            return decodeTrueFalseTable(grid, header);
        } else {
            return decodeChoiceTable(grid, header);
        }
    }

    private static String decodeTrueFalseTable(List<List<String>> grid, List<String> header) {
        StringBuilder sb = new StringBuilder();

        // 查找 T 行和 F 行
        List<String> tRow = null, fRow = null;
        for (int i = 1; i < grid.size(); i++) {
            List<String> row = grid.get(i);
            if (!row.isEmpty() && "T".equalsIgnoreCase(row.get(0))) {
                tRow = row;
            } else if (!row.isEmpty() && "F".equalsIgnoreCase(row.get(0))) {
                fRow = row;
            }
        }

        if (tRow == null || fRow == null) return "";

        // 解码每道题的答案
        for (int col = 1; col < header.size(); col++) {
            String qNum = header.get(col).trim();
            if (StringUtils.isBlank(qNum)) continue;

            String answer;
            if (col < tRow.size() && CHECK_MARK.equals(tRow.get(col))) {
                answer = "T";
            } else if (col < fRow.size() && CHECK_MARK.equals(fRow.get(col))) {
                answer = "F";
            } else {
                continue; // 该题未找到答案
            }

            sb.append(qNum).append(". ").append(answer).append("\n");
        }

        return sb.toString();
    }

    private static String decodeChoiceTable(List<List<String>> grid, List<String> header) {
        StringBuilder sb = new StringBuilder();
        String[] options = {"A", "B", "C", "D", "E", "F", "G", "H"};

        // 解码每道题的答案
        for (int col = 1; col < header.size(); col++) {
            String qNum = header.get(col).trim();
            if (StringUtils.isBlank(qNum)) continue;

            // 查找该列哪个选项行有√标记
            String answer = null;
            for (int row = 1; row < grid.size() && row - 1 < options.length; row++) {
                List<String> rowData = grid.get(row);
                if (col < rowData.size() && CHECK_MARK.equals(rowData.get(col))) {
                    answer = options[row - 1];
                    break;
                }
            }

            if (answer != null) {
                sb.append(qNum).append(". ").append(answer).append("\n");
            }
        }

        return sb.toString();
    }
}
