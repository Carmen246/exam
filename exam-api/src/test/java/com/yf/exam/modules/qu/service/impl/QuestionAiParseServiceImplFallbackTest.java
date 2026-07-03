package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import com.yf.exam.modules.qu.enums.QuType;
import com.yf.exam.modules.qu.support.ProgramContentFormatter;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionAiParseServiceImplFallbackTest {

    @Test
    void fallsBackToLocalReadProgramParserWhenAiReturnsNoQuestions() throws Exception {
        QuestionAiParseServiceImpl service = new QuestionAiParseServiceImpl();
        inject(service, "chatModel", new EmptyQuestionModel());
        inject(service, "programContentFormatter", new ProgramContentFormatter());

        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setRepoIds(Arrays.asList("repo-1"));
        reqDTO.setLevel(2);
        reqDTO.setText("四、程序阅读题（本大题共5小题，每小题4分，共20分）\n"
                + "39. 如下程序的输出结果是__________。\n"
                + "#include<stdio.h>\n"
                + "int main(){\n"
                + "  int a[3][3]={{1,2},{3,4},{5,6}}, i, j, s=0;\n"
                + "  for(i=0;i<3;i++)\n"
                + "    for(j=0;j<3;j++)\n"
                + "      if(i==j) s+=a[i][j];\n"
                + "  printf(\"%d\", s);\n"
                + "  return 0;\n"
                + "}\n"
                + "答案：11");

        QuestionParseRespDTO respDTO = service.parseSingleBatch(reqDTO, "20");

        assertEquals(1, respDTO.getQuestions().size());
        QuDetailDTO question = respDTO.getQuestions().get(0);
        assertEquals(QuType.READ_PROGRAM, question.getQuType());
        assertEquals(2, question.getLevel());
        assertEquals(Arrays.asList("repo-1"), question.getRepoIds());
        assertTrue(question.getContent().contains("如下程序的输出结果是__________。"));
        assertTrue(question.getContent().contains("#include<stdio.h>"));
        assertEquals(1, question.getAnswerList().size());
        assertEquals("11", question.getAnswerList().get(0).getContent());
        assertTrue(question.getAnswerList().get(0).getIsRight());
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class EmptyQuestionModel implements ChatLanguageModel {
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return Response.from(AiMessage.from("{\"questions\":[]}"));
        }
    }
}
