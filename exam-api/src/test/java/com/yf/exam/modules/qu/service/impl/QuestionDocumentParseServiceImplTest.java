package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionDocumentParseServiceImplTest {

    @Test
    void rejectsLegacyDocFiles() throws Exception {
        QuestionDocumentParseServiceImpl service = new QuestionDocumentParseServiceImpl();
        File file = File.createTempFile("legacy-word-", ".doc");
        Files.write(file.toPath(), "legacy doc".getBytes(StandardCharsets.UTF_8));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.extractRawText(file));

        assertTrue(ex.getMessage().contains("暂不支持旧版 Word .doc 文件"));
        assertTrue(ex.getMessage().contains(".docx"));
        assertFalse(ex.getMessage().contains("试题文档解析失败"));
    }
}
