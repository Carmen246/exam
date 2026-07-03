package com.yf.exam.core.utils.excel;

import com.yf.exam.core.utils.excel.annotation.ExcelField;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ExportExcelTest {

    @Test
    void writesStreamingWorkbookWithAutoSizedColumns() {
        assertDoesNotThrow(() -> {
            ExportExcel exportExcel = new ExportExcel("试题数据", SampleExportRow.class, 1);
            try {
                exportExcel
                        .setDataList(Collections.singletonList(new SampleExportRow("1", "题目内容")))
                        .write(new ByteArrayOutputStream());
            } finally {
                exportExcel.dispose();
            }
        });
    }

    static class SampleExportRow {
        @ExcelField(title = "题目序号", align = 2, sort = 1)
        private final String no;

        @ExcelField(title = "题目内容", align = 2, sort = 2)
        private final String content;

        SampleExportRow(String no, String content) {
            this.no = no;
            this.content = content;
        }

        public String getNo() {
            return no;
        }

        public String getContent() {
            return content;
        }
    }
}
