package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.modules.qu.dto.QuAnswerDTO;
import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import com.yf.exam.modules.qu.enums.QuType;
import com.yf.exam.modules.qu.support.ProgramContentFormatter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class QuServiceImplProgramValidationTest {

    @Test
    void programStemMayContainFunctionSignatureWhenReferenceAnswerHasCode() throws Exception {
        QuServiceImpl service = new QuServiceImpl();
        inject(service, "programContentFormatter", new ProgramContentFormatter());

        QuDetailDTO question = new QuDetailDTO();
        question.setQuType(QuType.PROGRAM);
        question.setContent("Write function float fx(float x), read x from keyboard, print result.");
        question.setAnswerList(new ArrayList<>());

        QuAnswerDTO answer = new QuAnswerDTO();
        answer.setContent("#include <stdio.h>\n"
                + "float fx(float x) {\n"
                + "    return x < 1 ? x : 2 * x - 1;\n"
                + "}\n"
                + "int main() {\n"
                + "    float x;\n"
                + "    scanf(\"%f\", &x);\n"
                + "    printf(\"%.2f\", fx(x));\n"
                + "    return 0;\n"
                + "}");
        answer.setIsRight(true);
        question.getAnswerList().add(answer);

        assertDoesNotThrow(() -> checkSubjectiveData(service, question));
    }

    private void checkSubjectiveData(QuServiceImpl service, QuDetailDTO question) throws Exception {
        Method method = QuServiceImpl.class.getDeclaredMethod("checkSubjectiveData", QuDetailDTO.class, String.class);
        method.setAccessible(true);
        method.invoke(service, question, "1");
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
