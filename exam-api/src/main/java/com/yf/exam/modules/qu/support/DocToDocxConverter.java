package com.yf.exam.modules.qu.support;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableCell;
import org.apache.poi.hwpf.usermodel.TableIterator;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 将旧版 Word .doc 转为 .docx，供后续 docx 解析链路使用。
 */
@Component
public class DocToDocxConverter {

    public File convertToDocxFile(File docFile) throws Exception {
        String baseName = docFile.getName();
        int dot = baseName.lastIndexOf('.');
        String nameWithoutExt = dot > 0 ? baseName.substring(0, dot) : baseName;
        File docxFile = new File(docFile.getParentFile(), nameWithoutExt + ".docx");
        try (InputStream in = new FileInputStream(docFile);
                OutputStream out = new FileOutputStream(docxFile)) {
            convert(in, out);
        }
        return docxFile;
    }

    public byte[] convert(byte[] docBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        convert(new ByteArrayInputStream(docBytes), out);
        return out.toByteArray();
    }

    public void convert(InputStream docInput, OutputStream docxOutput) throws Exception {
        try (HWPFDocument hwpfDoc = new HWPFDocument(docInput);
                XWPFDocument xwpfDoc = new XWPFDocument()) {
            copyContent(hwpfDoc, xwpfDoc);
            xwpfDoc.write(docxOutput);
        }
    }

    private void copyContent(HWPFDocument hwpfDoc, XWPFDocument xwpfDoc) {
        Range range = hwpfDoc.getRange();
        TableIterator tableIterator = new TableIterator(range);
        Table nextTable = tableIterator.hasNext() ? tableIterator.next() : null;

        for (int i = 0; i < range.numParagraphs(); i++) {
            Paragraph paragraph = range.getParagraph(i);
            if (paragraph.isInTable()) {
                if (nextTable != null) {
                    copyTable(xwpfDoc, nextTable);
                    nextTable = tableIterator.hasNext() ? tableIterator.next() : null;
                    while (i + 1 < range.numParagraphs() && range.getParagraph(i + 1).isInTable()) {
                        i++;
                    }
                }
                continue;
            }
            copyParagraph(xwpfDoc, paragraph);
        }
    }

    private void copyParagraph(XWPFDocument docx, Paragraph paragraph) {
        String text = normalizeParagraphText(paragraph.text());
        XWPFParagraph xPara = docx.createParagraph();
        if (StringUtils.isNotBlank(text)) {
            XWPFRun run = xPara.createRun();
            run.setText(text);
        }
    }

    private void copyTable(XWPFDocument docx, Table table) {
        int numRows = table.numRows();
        if (numRows <= 0) {
            return;
        }

        int numCols = 0;
        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            numCols = Math.max(numCols, table.getRow(rowIndex).numCells());
        }
        if (numCols <= 0) {
            return;
        }

        XWPFTable xTable = docx.createTable(numRows, numCols);
        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            TableRow row = table.getRow(rowIndex);
            XWPFTableRow xRow = xTable.getRow(rowIndex);
            for (int colIndex = 0; colIndex < row.numCells(); colIndex++) {
                TableCell cell = row.getCell(colIndex);
                String cellText = normalizeCellText(cell.text());
                XWPFTableCell xCell = xRow.getCell(colIndex);
                xCell.removeParagraph(0);
                XWPFParagraph para = xCell.addParagraph();
                if (StringUtils.isNotBlank(cellText)) {
                    XWPFRun run = para.createRun();
                    run.setText(cellText);
                }
            }
        }
    }

    private String normalizeParagraphText(String text) {
        if (text == null) {
            return "";
        }
        text = text.replace('\u0007', '\n').replace('\r', '\n');
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String normalizeCellText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u0007', ' ').replace('\r', ' ').trim();
    }
}
