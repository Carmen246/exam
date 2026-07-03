package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.modules.qu.dto.QuAnswerDTO;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import com.yf.exam.modules.qu.enums.QuType;
import com.yf.exam.modules.qu.support.ProgramContentFormatter;
import com.yf.exam.modules.qu.support.QuestionTextLocalNormalizer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionAiParseServiceImplFilterTest {

    @Test
    void removesBookSectionTitleThatAiReturnedAsProgramQuestion() throws Exception {
        QuestionAiParseServiceImpl service = new QuestionAiParseServiceImpl();
        inject(service, "programContentFormatter", new ProgramContentFormatter());

        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText("5 编程题\n\n5.1 C语言程序设计(基于CDIO思想)(第2版)问题求解与学习指导--书中例题程序\n\n"
                + "例1_1 在显示器上输出：hello world");
        reqDTO.setRepoIds(Arrays.asList("repo-1"));

        QuestionParseRespDTO respDTO = new QuestionParseRespDTO();
        respDTO.setQuestions(new ArrayList<>(Arrays.asList(
                programQuestion("5.1 C语言程序设计(基于CDIO思想)(第2版)问题求解与学习指导--书中例题程序",
                        "int main() { return 0; }"),
                programQuestion("编写程序，在显示器上输出 hello world。",
                        "#include <stdio.h>\nint main() {\n    printf(\"hello world\");\n    return 0;\n}"))));

        normalizeAndCheck(service, respDTO, reqDTO);

        assertEquals(1, respDTO.getQuestions().size());
        assertEquals("编写程序，在显示器上输出 hello world。", respDTO.getQuestions().get(0).getContent());
        assertEquals(1, respDTO.getCount());
    }

    @Test
    void expandsJudgeQuestionToTrueAndFalseOptionsWhenAiReturnsOnlyRightAnswer() throws Exception {
        QuestionAiParseServiceImpl service = new QuestionAiParseServiceImpl();
        inject(service, "programContentFormatter", new ProgramContentFormatter());

        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText("3. C语言中，宏替换没有数据类型限制。\n答案：正确");
        reqDTO.setRepoIds(Arrays.asList("repo-1"));

        QuestionParseRespDTO respDTO = new QuestionParseRespDTO();
        respDTO.setQuestions(new ArrayList<>(Arrays.asList(
                judgeQuestion("C语言中，宏替换没有数据类型限制。", "正确"))));

        normalizeAndCheck(service, respDTO, reqDTO);

        QuDetailDTO question = respDTO.getQuestions().get(0);
        assertEquals(2, question.getAnswerList().size());
        assertEquals("正确", question.getAnswerList().get(0).getContent());
        assertTrue(question.getAnswerList().get(0).getIsRight());
        assertEquals("错误", question.getAnswerList().get(1).getContent());
        assertFalse(question.getAnswerList().get(1).getIsRight());
    }

    @Test
    void extractsParenthesizedInlineOptionsForRadioQuestion() throws Exception {
        QuestionAiParseServiceImpl service = new QuestionAiParseServiceImpl();
        inject(service, "programContentFormatter", new ProgramContentFormatter());

        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText("22. 下列标识符中合法的标识符是（ ）。\nA) int2 B) if C) 2two D) a#b\n答案：A");
        reqDTO.setRepoIds(Arrays.asList("repo-1"));

        QuestionParseRespDTO respDTO = new QuestionParseRespDTO();
        QuDetailDTO question = radioQuestion("下列标识符中合法的标识符是（ ）。 A) int2 B) if C) 2two D) a#b",
                "int2");
        respDTO.setQuestions(new ArrayList<>(Arrays.asList(question)));

        normalizeAndCheck(service, respDTO, reqDTO);

        question = respDTO.getQuestions().get(0);
        assertEquals("下列标识符中合法的标识符是（ ）。", question.getContent());
        assertEquals(4, question.getAnswerList().size());
        assertEquals("int2", question.getAnswerList().get(0).getContent());
        assertTrue(question.getAnswerList().get(0).getIsRight());
        assertEquals("if", question.getAnswerList().get(1).getContent());
        assertEquals("2two", question.getAnswerList().get(2).getContent());
        assertEquals("a#b", question.getAnswerList().get(3).getContent());
    }

    @Test
    void extractsParenthesizedInlineOptionsForReadProgramResultQuestion() throws Exception {
        QuestionAiParseServiceImpl service = new QuestionAiParseServiceImpl();
        inject(service, "programContentFormatter", new ProgramContentFormatter());

        String sourceText = "35. 若运行时给变量x输入12,则以下程序的运行结果是_______。\n"
                + "#include<stdio.h>\n"
                + "int main(){\n"
                + "int x, y;\n"
                + "scanf(\"%d\", &x); y=x>12?x+10:x-12;\n"
                + "printf(\"%d\\n\", y);\n"
                + "return 0; } A) 0 B) 22 C) 12 D) 10\n"
                + "答案：A";
        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText(sourceText);
        reqDTO.setRepoIds(Arrays.asList("repo-1"));

        QuestionParseRespDTO respDTO = new QuestionParseRespDTO();
        QuDetailDTO question = readProgramQuestion(sourceText, "0");
        respDTO.setQuestions(new ArrayList<>(Arrays.asList(question)));

        normalizeAndCheck(service, respDTO, reqDTO);

        question = respDTO.getQuestions().get(0);
        assertEquals(4, question.getAnswerList().size());
        assertEquals("0", question.getAnswerList().get(0).getContent());
        assertTrue(question.getAnswerList().get(0).getIsRight());
        assertEquals("22", question.getAnswerList().get(1).getContent());
        assertEquals("12", question.getAnswerList().get(2).getContent());
        assertEquals("10", question.getAnswerList().get(3).getContent());
        assertFalse(question.getContent().contains("A) 0"));
    }

    @Test
    void splitParseBatchesKeepsProgramDesignSectionQuestions() throws Exception {
        QuestionAiParseServiceImpl service = new QuestionAiParseServiceImpl();
        injectProperties(service, 1800);

        String text = "四、程序阅读题（本大题共5小题，每小题4分，共20分）\n"
                + "39. 以下程序的输出结果是__________。\n"
                + "#include<stdio.h>\n"
                + "int main(){return 0;} A) 18 B) 19 C) 20 D) 21\n"
                + "答案：A\n\n"
                + "五、程序设计题（本大题共3小题，每小题10分，共30分）\n"
                + "1. 用 for 语句求 1 到 100 中是 7 的倍数的数的和。\n"
                + "参考答案：\n"
                + "#include <stdio.h>\n"
                + "int main(){int i,sum=0;for(i=1;i<=100;i++){if(i%7==0)sum+=i;}printf(\"%d\",sum);return 0;}\n"
                + "2. 有分段函数如下定义，编写一个函数 float fx(float x)，要求给一个 x，根据分段函数返回 y 的值。\n"
                + "参考答案：\n"
                + "#include <stdio.h>\n"
                + "float fx(float x){if(x<1)return x;if(x<10)return 2*x-1;return 3*x-11;}\n"
                + "3. 有 5 个学生，每个学生有 3 门课的成绩，从文件 f1.txt 中读入数据，计算平均成绩并写入 f2.txt。\n"
                + "参考答案：\n"
                + "#include <stdio.h>\n"
                + "int main(){return 0;}";

        List<String> batches = service.splitParseBatches(text);

        long programBatchCount = batches.stream()
                .filter(batch -> batch.contains("程序设计题") && batch.contains("用 for 语句求")
                        && batch.contains("float fx") && !batch.contains("以下程序的输出结果"))
                .count();
        assertEquals(1, programBatchCount);
    }

    @Test
    void keepsProgramDesignQuestionsWithoutReferenceProgramDuringPreview() throws Exception {
        QuestionAiParseServiceImpl service = new QuestionAiParseServiceImpl();
        inject(service, "programContentFormatter", new ProgramContentFormatter());

        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText("五、程序设计题（本大题共3小题，每小题10分，共30分）\n"
                + "1. 用 for 语句求 1 到 100 中是 7 的倍数的数的和。\n"
                + "2. 有分段函数如下定义，编写一个函数 float fx(float x)，要求给一个 x，根据分段函数返回 y 的值。\n"
                + "3. 有 5 个学生，每个学生有 3 门课的成绩，从文件 f1.txt 中读入数据，计算平均成绩并写入 f2.txt。");
        reqDTO.setRepoIds(Arrays.asList("repo-1"));

        QuestionParseRespDTO respDTO = new QuestionParseRespDTO();
        respDTO.setQuestions(new ArrayList<>(Arrays.asList(
                programQuestionWithoutAnswer("用 for 语句求 1 到 100 中是 7 的倍数的数的和。"),
                programQuestionWithoutAnswer("有分段函数如下定义，编写一个函数 float fx(float x)，要求给一个 x，根据分段函数返回 y 的值。"),
                programQuestionWithoutAnswer("有 5 个学生，每个学生有 3 门课的成绩，从文件 f1.txt 中读入数据，计算平均成绩并写入 f2.txt。"))));

        normalizeAndCheck(service, respDTO, reqDTO);

        assertEquals(3, respDTO.getQuestions().size());
        assertEquals(QuType.PROGRAM, respDTO.getQuestions().get(0).getQuType());
        assertEquals(QuType.PROGRAM, respDTO.getQuestions().get(1).getQuType());
        assertEquals(QuType.PROGRAM, respDTO.getQuestions().get(2).getQuType());
    }

    @Test
    void promptsMustNotSkipProgramDesignQuestionsWithoutReferenceProgram() throws Exception {
        QuestionAiParseServiceImpl service = new QuestionAiParseServiceImpl();

        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText("五、程序设计题（本大题共3小题，每小题10分，共30分）\n"
                + "1. 用 for 语句求 1 到 100 中是 7 的倍数的数的和。");
        reqDTO.setRepoIds(Arrays.asList("repo-1"));

        String systemPrompt = getStaticString("SYSTEM_PROMPT");
        String userPrompt = buildUserPrompt(service, reqDTO);

        assertTrue(systemPrompt.contains("answerList=[]"));
        assertTrue(systemPrompt.contains("也必须输出该题"));
        assertTrue(userPrompt.contains("answerList=[]"));
        assertTrue(userPrompt.contains("禁止跳过"));
    }

    @Test
    void localNormalizeKeepsFirstSingleChoiceWithParenthesizedOptions() {
        QuestionTextLocalNormalizer normalizer = new QuestionTextLocalNormalizer();

        String normalized = normalizer.normalize("二、单选题（本大题共20小题，每题1分，共20分）\n"
                + "1. C 语言规定，必须用（ ）作为主函数名。\n"
                + "A)Function    B)include    C)main        D)stdio\n"
                + "2. 在C语言中，每个语句是用（ ）结束。 A)句号 B)逗号 C)分号 D)括号");

        assertTrue(normalized.contains("必须用（ ）作为主函数名。"));
        assertTrue(normalized.contains("A. Function"));
        assertTrue(normalized.contains("B. include"));
        assertTrue(normalized.contains("C. main"));
        assertTrue(normalized.contains("D. stdio"));
        assertTrue(normalized.contains("2. 在C语言中，每个语句是用（ ）结束。"));
    }

    private QuDetailDTO programQuestion(String content, String answerCode) {
        QuDetailDTO question = new QuDetailDTO();
        question.setQuType(QuType.PROGRAM);
        question.setLevel(1);
        question.setContent(content);
        question.setAnswerList(new ArrayList<>());

        QuAnswerDTO answer = new QuAnswerDTO();
        answer.setContent(answerCode);
        answer.setIsRight(true);
        question.getAnswerList().add(answer);
        return question;
    }

    private QuDetailDTO programQuestionWithoutAnswer(String content) {
        QuDetailDTO question = new QuDetailDTO();
        question.setQuType(QuType.PROGRAM);
        question.setLevel(1);
        question.setContent(content);
        question.setAnswerList(new ArrayList<>());
        return question;
    }

    private QuDetailDTO judgeQuestion(String content, String rightAnswer) {
        QuDetailDTO question = new QuDetailDTO();
        question.setQuType(QuType.JUDGE);
        question.setLevel(1);
        question.setContent(content);
        question.setAnswerList(new ArrayList<>());

        QuAnswerDTO answer = new QuAnswerDTO();
        answer.setContent(rightAnswer);
        answer.setIsRight(true);
        question.getAnswerList().add(answer);
        return question;
    }

    private QuDetailDTO radioQuestion(String content, String rightAnswer) {
        QuDetailDTO question = new QuDetailDTO();
        question.setQuType(QuType.RADIO);
        question.setLevel(1);
        question.setContent(content);
        question.setAnswerList(new ArrayList<>());

        QuAnswerDTO answer = new QuAnswerDTO();
        answer.setContent(rightAnswer);
        answer.setIsRight(true);
        question.getAnswerList().add(answer);
        return question;
    }

    private QuDetailDTO readProgramQuestion(String content, String rightAnswer) {
        QuDetailDTO question = new QuDetailDTO();
        question.setQuType(QuType.READ_PROGRAM);
        question.setLevel(1);
        question.setContent(content);
        question.setAnswerList(new ArrayList<>());

        QuAnswerDTO answer = new QuAnswerDTO();
        answer.setContent(rightAnswer);
        answer.setIsRight(true);
        question.getAnswerList().add(answer);
        return question;
    }

    private void normalizeAndCheck(QuestionAiParseServiceImpl service, QuestionParseRespDTO respDTO,
            QuestionParseReqDTO reqDTO) throws Exception {
        Method method = QuestionAiParseServiceImpl.class.getDeclaredMethod("normalizeAndCheck",
                QuestionParseRespDTO.class, QuestionParseReqDTO.class);
        method.setAccessible(true);
        method.invoke(service, respDTO, reqDTO);
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String getStaticString(String fieldName) throws Exception {
        Field field = QuestionAiParseServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private String buildUserPrompt(QuestionAiParseServiceImpl service, QuestionParseReqDTO reqDTO) throws Exception {
        Method method = QuestionAiParseServiceImpl.class.getDeclaredMethod("buildUserPrompt", QuestionParseReqDTO.class);
        method.setAccessible(true);
        return (String) method.invoke(service, reqDTO);
    }

    private void injectProperties(QuestionAiParseServiceImpl service, int maxTextLength) throws Exception {
        com.yf.exam.modules.qu.config.QuestionAiProperties properties =
                new com.yf.exam.modules.qu.config.QuestionAiProperties();
        properties.setMaxTextLength(maxTextLength);
        inject(service, "properties", properties);
    }
}
