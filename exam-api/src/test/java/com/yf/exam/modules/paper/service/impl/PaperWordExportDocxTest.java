package com.yf.exam.modules.paper.service.impl;

import com.yf.exam.modules.paper.dto.ext.PaperQuDetailDTO;
import com.yf.exam.modules.qu.enums.QuType;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ObjectFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

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

    @Test
    void questionSectionPreservesLineBreaksInProgramContent() throws Exception {
        PaperWordExportServiceImpl service = new PaperWordExportServiceImpl();
        WordprocessingMLPackage document = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = document.getMainDocumentPart();
        ObjectFactory factory = Context.getWmlObjectFactory();

        PaperQuDetailDTO question = new PaperQuDetailDTO();
        question.setQuType(QuType.FILL_PROGRAM);
        question.setScore(1);
        question.setContent("Read the program.\n#include<stdio.h>\nint main(){\n  return 0;\n}");
        question.setAnswerList(Collections.emptyList());

        Method method = PaperWordExportServiceImpl.class.getDeclaredMethod(
                "addQuestionSection", MainDocumentPart.class, ObjectFactory.class, String.class, List.class, int.class);
        method.setAccessible(true);
        method.invoke(service, main, factory, "Program fill", Collections.singletonList(question), 1);

        String xml = main.getXML();
        assertTrue(xml.contains("<w:br"));
        assertTrue(xml.contains("#include"));
    }
}
