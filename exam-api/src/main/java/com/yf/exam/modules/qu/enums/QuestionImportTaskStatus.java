package com.yf.exam.modules.qu.enums;

/**
 * AI 导入任务状态
 */
public enum QuestionImportTaskStatus {

    PENDING("等待处理"),
    EXTRACTING("提取文档文本"),
    NORMALIZING("AI清洗文本"),
    PARSING("AI解析试题"),
    COMPLETED("解析完成"),
    FAILED("处理失败");

    private final String label;

    QuestionImportTaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
