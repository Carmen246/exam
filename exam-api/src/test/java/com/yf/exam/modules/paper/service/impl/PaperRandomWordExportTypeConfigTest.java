package com.yf.exam.modules.paper.service.impl;

import com.yf.exam.modules.paper.dto.request.PaperRandomWordExportReqDTO;
import com.yf.exam.modules.qu.enums.QuType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaperRandomWordExportTypeConfigTest {

    @Test
    void randomWordExportAcceptsDynamicQuestionTypeConfigs() throws Exception {
        Class<?> configClass = findNestedClass(PaperRandomWordExportReqDTO.class, "QuestionTypeConfig");
        Object config = configClass.newInstance();
        invoke(config, "setQuType", Integer.class, QuType.FILL);
        invoke(config, "setCount", Integer.class, 4);
        invoke(config, "setScore", Integer.class, 2);

        PaperRandomWordExportReqDTO reqDTO = new PaperRandomWordExportReqDTO();
        invoke(reqDTO, "setTypes", List.class, Collections.singletonList(config));

        PaperWordExportServiceImpl service = new PaperWordExportServiceImpl();
        Method method = PaperWordExportServiceImpl.class
                .getDeclaredMethod("randomTypeConfigs", PaperRandomWordExportReqDTO.class);
        method.setAccessible(true);

        List<?> configs = (List<?>) method.invoke(service, reqDTO);

        assertEquals(1, configs.size());
        Object normalized = configs.get(0);
        assertEquals(QuType.FILL, invoke(normalized, "getQuType"));
        assertEquals(4, invoke(normalized, "getCount"));
        assertEquals(2, invoke(normalized, "getScore"));
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

    private Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private void invoke(Object target, String methodName, Class<?> argType, Object value) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, argType);
        method.setAccessible(true);
        method.invoke(target, value);
    }
}
