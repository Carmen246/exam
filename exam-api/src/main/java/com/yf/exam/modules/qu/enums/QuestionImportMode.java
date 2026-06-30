package com.yf.exam.modules.qu.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * AI 导入处理模式
 */
public enum QuestionImportMode {

    /** 本地清洗 + 直接解析，无兜底 */
    FAST("极速模式"),

    /** 本地清洗 + 直接解析，失败批次深度清洗后重试（默认） */
    SMART("智能模式"),

    /** 每批先 AI 深度清洗再解析 */
    DEEP("深度模式");

    private final String label;

    QuestionImportMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static QuestionImportMode from(String value) {
        if (StringUtils.isBlank(value)) {
            return SMART;
        }
        try {
            return QuestionImportMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SMART;
        }
    }
}
