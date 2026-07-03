package com.yf.exam.modules.qu.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocTextExtractorTest {

    @Test
    void restoresMissingAutoNumbersFromLegacyDocText() {
        DocTextExtractor extractor = new DocTextExtractor();

        String normalized = extractor.restoreMissingQuestionNumbers("二、 单选题（本大题共20小题，每题1分，共20分）\n"
                + "C 语言规定，必须用（ ）作为主函数名。\n"
                + "A)Function    B)include    C)main        D)stdio\n"
                + "在C语言中，每个语句是用（ ）结束。 A)句号 B)逗号 C)分号 D)括号\n\n"
                + "五、程序设计题（本大题共3小题，每小题10分，共30分）\n"
                + "用 for 语句求 1 到 100 中是 7 的倍数的数的和。\n"
                + "有分段函数如下定义：\n"
                + "编写一个函数 float fx(float x)，要求给一个 x，根据分段函数返回 y 的值。");

        assertTrue(normalized.contains("1. C 语言规定，必须用（ ）作为主函数名。"));
        assertTrue(normalized.contains("2. 在C语言中，每个语句是用（ ）结束。"));
        assertTrue(normalized.contains("1. 用 for 语句求 1 到 100 中是 7 的倍数的数的和。"));
        assertTrue(normalized.contains("2. 有分段函数如下定义："));
        assertTrue(normalized.contains("编写一个函数 float fx(float x)，要求给一个 x，根据分段函数返回 y 的值。"));
    }
}
