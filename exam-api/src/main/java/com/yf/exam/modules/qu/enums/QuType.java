package com.yf.exam.modules.qu.enums;


/**
 * 题目类型
 * @author bool 
 * @date 2019-10-30 13:11
 */
public interface QuType {

    /** 单选题 */
    Integer RADIO = 1;

    /** 多选题 */
    Integer MULTI = 2;

    /** 判断题 */
    Integer JUDGE = 3;

    /** 填空题 */
    Integer FILL = 4;

    /** 程序填空题 */
    Integer FILL_PROGRAM = 5;

    /** 阅读程序写结果题 */
    Integer READ_PROGRAM = 6;

    /** 编程题 */
    Integer PROGRAM = 7;

    /** 程序改错题 */
    Integer FIX_PROGRAM = 8;

    /** 综合应用题 */
    Integer COMPREHENSIVE = 9;

    static boolean isObjective(Integer quType) {
        return RADIO.equals(quType) || MULTI.equals(quType) || JUDGE.equals(quType);
    }

    static boolean isSubjective(Integer quType) {
        return quType != null && quType >= FILL && quType <= COMPREHENSIVE;
    }

    static boolean isFillType(Integer quType) {
        return FILL.equals(quType) || FILL_PROGRAM.equals(quType);
    }
}
