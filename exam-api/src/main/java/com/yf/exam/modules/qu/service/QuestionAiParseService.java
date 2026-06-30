package com.yf.exam.modules.qu.service;

import com.yf.exam.modules.qu.dto.QuestionImportReqDTO;
import com.yf.exam.modules.qu.dto.QuestionImportRespDTO;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextReqDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextRespDTO;
import com.yf.exam.modules.qu.support.ImportTaskProgressListener;

public interface QuestionAiParseService {

    QuestionParseRespDTO parseQuestions(QuestionParseReqDTO reqDTO);

    QuestionParseRespDTO parseQuestions(QuestionParseReqDTO reqDTO, ImportTaskProgressListener listener);

    QuestionImportRespDTO importQuestions(QuestionImportReqDTO reqDTO);

    QuestionNormalizeTextRespDTO normalizeText(QuestionNormalizeTextReqDTO reqDTO);

    QuestionNormalizeTextRespDTO normalizeText(QuestionNormalizeTextReqDTO reqDTO, ImportTaskProgressListener listener);
}