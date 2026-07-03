package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 docx 提取文本，优先识别下划线/高亮 run 作为程序填空答案
 */
@Component
public class DocxBlankAwareTextExtractor {

    private static final String UNREADABLE_IMAGE_PLACEHOLDER =
            "【图片内容未识别，请将图片中的公式/题干转为文字后再导入】";

    private static final Pattern OFFICE_MATH_BLOCK = Pattern.compile(
            "<(?:\\w+:)?oMath(?:\\s[^>]*)?>.*?</(?:\\w+:)?oMath>", Pattern.DOTALL);
    private static final Pattern XML_TEXT_NODE = Pattern.compile(
            "<(?:\\w+:)?t(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?t>", Pattern.DOTALL);

    @Autowired
    private FillProgramBlankProcessor blankProcessor;

    public String extract(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            Map<String, Integer> numberingCounters = new HashMap<>();
            boolean seenExamHeader = false;
            
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph para = (XWPFParagraph) element;
                    String text = getParagraphText(para);
                    
                    // 检测试卷头部特征：同一段落中同时包含"考试科目"和"考试类型"
                    if (text.contains("考试科目") && text.contains("考试类型")) {
                        if (seenExamHeader) {
                            // 第二次出现，说明进入了答题纸模板部分，停止提取
                            break;
                        }
                        seenExamHeader = true;
                    } else if (seenExamHeader && text.contains("专业班级") && text.contains("学号")) {
                        // 已见过试卷头部后，再次出现"专业班级"+"学号"，
                        // 说明进入了答题纸模板的封面区域，停止提取
                        break;
                    } else if (seenExamHeader && text.contains("浙江科技学院")) {
                        // 已见过试卷头部后，再次出现"浙江科技学院"，
                        // 说明进入了答题纸模板的封面区域，停止提取
                        break;
                    }
                    
                    appendParagraph(sb, para, numberingCounters);
                } else if (element instanceof XWPFTable) {
                    appendTable(sb, (XWPFTable) element, numberingCounters);
                }
            }
            String text = sb.toString();
            return blankProcessor.markSpacePaddedBlanks(text);
        }
    }

    private String getParagraphText(XWPFParagraph paragraph) {
        StringBuilder sb = new StringBuilder();
        if (paragraph.getRuns() != null) {
            for (XWPFRun run : paragraph.getRuns()) {
                String t = run.getText(0);
                if (t != null) {
                    sb.append(t);
                }
            }
        }
        appendOfficeMathText(sb, extractOfficeMathText(paragraph));
        appendUnreadableImagePlaceholder(sb, extractUnreadableImagePlaceholder(paragraph));
        return sb.toString();
    }

    private void appendTable(StringBuilder sb, XWPFTable table, Map<String, Integer> numberingCounters) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    appendParagraph(sb, paragraph, numberingCounters);
                }
            }
        }
    }

    private void appendParagraph(StringBuilder sb, XWPFParagraph paragraph, Map<String, Integer> numberingCounters) {
        List<XWPFRun> runs = paragraph.getRuns();
        String mathText = extractOfficeMathText(paragraph);
        String imageText = extractUnreadableImagePlaceholder(paragraph);
        if (runs == null || runs.isEmpty()) {
            if (StringUtils.isNotBlank(mathText)) {
                appendListNumberIfNeeded(sb, paragraph, mathText, numberingCounters);
                sb.append(mathText);
            }
            appendUnreadableImagePlaceholder(sb, imageText);
            sb.append("\n");
            return;
        }

        StringBuilder paragraphText = new StringBuilder();
        StringBuilder fillBuffer = new StringBuilder();
        for (XWPFRun run : runs) {
            appendRun(paragraphText, fillBuffer, run);
        }
        flushFillBuffer(paragraphText, fillBuffer);
        appendOfficeMathText(paragraphText, mathText);
        appendUnreadableImagePlaceholder(paragraphText, imageText);
        appendListNumberIfNeeded(sb, paragraph, paragraphText.toString(), numberingCounters);
        sb.append(paragraphText);
        sb.append("\n");
    }

    private void appendOfficeMathText(StringBuilder sb, String mathText) {
        if (StringUtils.isBlank(mathText) || sb.indexOf(mathText) >= 0) {
            return;
        }
        if (sb.length() > 0 && !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.append(' ');
        }
        sb.append(mathText);
    }

    private void appendUnreadableImagePlaceholder(StringBuilder sb, String imageText) {
        if (StringUtils.isBlank(imageText) || sb.indexOf(imageText) >= 0) {
            return;
        }
        if (sb.length() > 0 && !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.append(' ');
        }
        sb.append(imageText);
    }

    private String extractUnreadableImagePlaceholder(XWPFParagraph paragraph) {
        String xml = paragraph.getCTP().xmlText();
        if (xml.contains("<w:drawing") || xml.contains("<w:pict")
                || xml.contains("<v:shape") || xml.contains("<v:imagedata")
                || xml.contains("<pic:pic")) {
            return UNREADABLE_IMAGE_PLACEHOLDER;
        }
        return "";
    }

    private String extractOfficeMathText(XWPFParagraph paragraph) {
        String xml = paragraph.getCTP().xmlText();
        Matcher blockMatcher = OFFICE_MATH_BLOCK.matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (blockMatcher.find()) {
            Matcher textMatcher = XML_TEXT_NODE.matcher(blockMatcher.group());
            while (textMatcher.find()) {
                sb.append(unescapeXmlText(textMatcher.group(1)));
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private String unescapeXmlText(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private void appendListNumberIfNeeded(StringBuilder sb, XWPFParagraph paragraph, String text,
                                          Map<String, Integer> numberingCounters) {
        if (StringUtils.isBlank(text)) {
            return;
        }

        BigInteger numId = paragraph.getNumID();
        if (numId == null || shouldKeepParagraphUnnumbered(text)) {
            return;
        }

        // POI 5.2.5 的 XWPFParagraph 没有暴露 getNumIlv() 方法，通过 CT API 访问编号层级
        BigInteger ilvl = null;
        if (paragraph.getCTP().isSetPPr()
                && paragraph.getCTP().getPPr().isSetNumPr()
                && paragraph.getCTP().getPPr().getNumPr().isSetIlvl()) {
            ilvl = paragraph.getCTP().getPPr().getNumPr().getIlvl().getVal();
        }
        String key = numId.toString() + ":" + (ilvl == null ? "0" : ilvl.toString());
        Integer next = numberingCounters.get(key);
        next = next == null ? 1 : next + 1;
        numberingCounters.put(key, next);
        sb.append(next).append(". ");
    }

    private boolean shouldKeepParagraphUnnumbered(String text) {
        String value = text.trim();
        return value.matches("^\\d+[\\.．、].*")
                || value.matches("^[（(]\\s*\\d+\\s*[）)].*")
                || value.matches("^[A-Ha-h][\\.、)）].*")
                || value.matches("^([一二三四五六七八九十]+、)?(判断题|单选题|多选题|程序填空题|程序阅读题|程序设计题).*本大题共.*")
                || value.matches("^(得分|题序|签名|考试科目|考试类型|考试方式|完成时限|拟题人|审核人|批准人).*")
                || value.matches("^(应将全部答案写在答卷纸|编程题应写明题号|考试完成后|不要另添卷纸|否则作无效处理|写在背面).*")
                || value.matches("^(命题|说明)[：:].*");
    }

    private void appendRun(StringBuilder sb, StringBuilder fillBuffer, XWPFRun run) {
        String text = run.getText(0);
        if (text == null || text.isEmpty()) {
            return;
        }

        if (isFillBlankRun(run)) {
            fillBuffer.append(text);
            return;
        }
        flushFillBuffer(sb, fillBuffer);
        sb.append(text);
    }

    private void flushFillBuffer(StringBuilder sb, StringBuilder fillBuffer) {
        if (fillBuffer.length() == 0) {
            return;
        }

        String answer = fillBuffer.toString().trim();
        if (StringUtils.isNotBlank(answer)) {
            sb.append(FillProgramBlankProcessor.FILL_PREFIX)
                    .append(answer)
                    .append(FillProgramBlankProcessor.FILL_SUFFIX);
        } else {
            sb.append(fillBuffer);
        }
        fillBuffer.setLength(0);
    }

    private boolean isFillBlankRun(XWPFRun run) {
        UnderlinePatterns underline = run.getUnderline();
        if (underline != null && underline != UnderlinePatterns.NONE) {
            return true;
        }

        String color = run.getColor();
        if (StringUtils.isNotBlank(color) && isBlueLike(color)) {
            return true;
        }

        if (run.isHighlighted() || run.isStrikeThrough()) {
            return false;
        }

        return false;
    }

    private boolean isBlueLike(String color) {
        String value = color.trim().toUpperCase();
        return "0000FF".equals(value)
                || "0070C0".equals(value)
                || "2E75B6".equals(value)
                || "4472C4".equals(value)
                || "5B9BD5".equals(value);
    }
}
