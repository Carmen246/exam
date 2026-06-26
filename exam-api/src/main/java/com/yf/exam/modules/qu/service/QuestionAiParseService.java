package com.yf.exam.modules.qu.service;

import com.yf.exam.modules.qu.dto.QuestionImportReqDTO;
import com.yf.exam.modules.qu.dto.QuestionImportRespDTO;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;

public interface QuestionAiParseService {

    QuestionParseRespDTO parseQuestions(QuestionParseReqDTO reqDTO);

    QuestionImportRespDTO importQuestions(QuestionImportReqDTO reqDTO);
}