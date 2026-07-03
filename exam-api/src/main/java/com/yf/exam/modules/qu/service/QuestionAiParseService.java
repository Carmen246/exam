package com.yf.exam.modules.qu.service;

import com.yf.exam.modules.qu.dto.QuestionImportReqDTO;
import com.yf.exam.modules.qu.dto.QuestionImportRespDTO;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextReqDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextRespDTO;
import com.yf.exam.modules.qu.support.ImportTaskProgressListener;

import java.util.List;

public interface QuestionAiParseService {

    QuestionParseRespDTO parseQuestions(QuestionParseReqDTO reqDTO);

    QuestionParseRespDTO parseQuestions(QuestionParseReqDTO reqDTO, ImportTaskProgressListener listener);

    QuestionImportRespDTO importQuestions(QuestionImportReqDTO reqDTO);

    QuestionNormalizeTextRespDTO normalizeText(QuestionNormalizeTextReqDTO reqDTO);

    QuestionNormalizeTextRespDTO normalizeText(QuestionNormalizeTextReqDTO reqDTO, ImportTaskProgressListener listener);

    List<String> splitParseBatches(String text);

    QuestionParseRespDTO parseSingleBatch(QuestionParseReqDTO reqDTO, String batchNo);

    String normalizeSingleBatch(String text, String batchNo);

    /** 测试当前 AI 后端（含 RAGFlow）是否连通 */
    String pingAi();
}