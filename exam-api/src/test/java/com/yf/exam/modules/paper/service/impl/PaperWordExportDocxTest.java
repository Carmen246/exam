package com.yf.exam.modules.paper.service.impl;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperWordExportDocxTest {

    @Test
    void docx4jCanSaveGeneratedWordDocument() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertDoesNotThrow(() -> {
            WordprocessingMLPackage document = WordprocessingMLPackage.createPackage();
            document.getMainDocumentPart().addParagraphOfText("random paper");
            document.save(outputStream);
        });

        assertTrue(outputStream.size() > 0);
    }
}
