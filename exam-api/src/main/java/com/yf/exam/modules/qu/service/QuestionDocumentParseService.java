package com.yf.exam.modules.qu.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface QuestionDocumentParseService {

    String parseText(MultipartFile file);

    String parseText(File file);

    /** 提取原始文本（不做本地规则清洗） */
    String extractRawText(File file);

    /** 提取答案文档文本（优先解析表格格式的√答案网格，失败时回退到普通文本提取） */
    String extractAnswerText(File file);

    /** 本地规则清洗（不调用 AI） */
    String normalizeLocally(String text);

    /** 估算文档中的题目数量 */
    int countQuestionBlocks(String text);
}
