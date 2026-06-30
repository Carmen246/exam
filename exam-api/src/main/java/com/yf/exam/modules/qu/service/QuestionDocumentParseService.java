package com.yf.exam.modules.qu.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface QuestionDocumentParseService {

    String parseText(MultipartFile file);

    String parseText(File file);

    /** 提取原始文本（不做本地规则清洗） */
    String extractRawText(File file);

    /** 本地规则清洗（不调用 AI） */
    String normalizeLocally(String text);

    /** 估算文档中的题目数量 */
    int countQuestionBlocks(String text);
}
