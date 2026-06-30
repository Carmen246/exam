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
import java.util.List;

/**
 * 从 docx 提取文本，优先识别下划线/高亮 run 作为程序填空答案
 */
@Component
public class DocxBlankAwareTextExtractor {

    @Autowired
    private FillProgramBlankProcessor blankProcessor;

    public String extract(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    appendParagraph(sb, (XWPFParagraph) element);
                } else if (element instanceof XWPFTable) {
                    appendTable(sb, (XWPFTable) element);
                }
            }
            String text = sb.toString();
            return blankProcessor.markSpacePaddedBlanks(text);
        }
    }

    private void appendTable(StringBuilder sb, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    appendParagraph(sb, paragraph);
                }
            }
        }
    }

    private void appendParagraph(StringBuilder sb, XWPFParagraph paragraph) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            sb.append("\n");
            return;
        }

        StringBuilder fillBuffer = new StringBuilder();
        for (XWPFRun run : runs) {
            appendRun(sb, fillBuffer, run);
        }
        flushFillBuffer(sb, fillBuffer);
        sb.append("\n");
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
