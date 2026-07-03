package com.yf.exam.modules.exam.service.impl;

import com.yf.exam.modules.exam.dto.ExamRepoDTO;
import com.yf.exam.modules.exam.dto.ext.ExamRepoExtDTO;
import com.yf.exam.modules.exam.dto.request.ExamSaveReqDTO;
import com.yf.exam.modules.paper.entity.PaperQu;
import com.yf.exam.modules.paper.service.impl.PaperServiceImpl;
import com.yf.exam.modules.qu.entity.Qu;
import com.yf.exam.modules.qu.enums.QuType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExamDynamicTypeConfigTest {

    @Test
    void examScoreIncludesDynamicQuestionTypeConfigs() throws Exception {
        ExamRepoExtDTO repo = new ExamRepoExtDTO();
        setTypes(repo, Collections.singletonList(typeConfig(QuType.FILL_PROGRAM, 3, 4)));

        ExamSaveReqDTO reqDTO = new ExamSaveReqDTO();
        reqDTO.setRepoList(Collections.singletonList(repo));

        Method calcScore = ExamServiceImpl.class.getDeclaredMethod("calcScore", ExamSaveReqDTO.class);
        calcScore.setAccessible(true);
        calcScore.invoke(new ExamServiceImpl(), reqDTO);

        assertEquals(12, reqDTO.getTotalScore());
    }

    @Test
    void paperQuestionUsesDynamicConfiguredScoreForSubjectiveType() throws Exception {
        ExamRepoDTO repo = new ExamRepoDTO();
        setTypes(repo, Collections.singletonList(typeConfig(QuType.READ_PROGRAM, 1, 6)));

        Qu question = new Qu();
        question.setId("q1");
        question.setQuType(QuType.READ_PROGRAM);

        Method processPaperQu = PaperServiceImpl.class.getDeclaredMethod("processPaperQu", ExamRepoDTO.class, Qu.class);
        processPaperQu.setAccessible(true);
        PaperQu paperQu = (PaperQu) processPaperQu.invoke(new PaperServiceImpl(), repo, question);

        assertEquals(6, paperQu.getScore());
        assertEquals(0, paperQu.getActualScore());
    }

    private Object typeConfig(Integer quType, Integer count, Integer score) throws Exception {
        Class<?> configClass = findNestedClass(ExamRepoDTO.class, "QuestionTypeConfig");
        Object config = configClass.newInstance();
        invoke(config, "setQuType", Integer.class, quType);
        invoke(config, "setCount", Integer.class, count);
        invoke(config, "setScore", Integer.class, score);
        return config;
    }

    private void setTypes(ExamRepoDTO repo, List<?> configs) throws Exception {
        invoke(repo, "setTypes", List.class, configs);
    }

    private Class<?> findNestedClass(Class<?> owner, String simpleName) {
        for (Class<?> nested : owner.getDeclaredClasses()) {
            if (simpleName.equals(nested.getSimpleName())) {
                return nested;
            }
        }
        assertNotNull(null, "Expected nested class " + simpleName);
        return null;
    }

    private void invoke(Object target, String methodName, Class<?> argType, Object value) throws Exception {
        Method method = target.getClass().getMethod(methodName, argType);
        method.setAccessible(true);
        method.invoke(target, value);
    }
}
