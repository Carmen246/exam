package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.config.QuestionAiConfigProvider;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import com.yf.exam.modules.qu.entity.QuestionImportBatchState;
import com.yf.exam.modules.qu.enums.QuestionImportMode;
import com.yf.exam.modules.qu.service.QuestionAiParseService;
import com.yf.exam.modules.qu.service.QuestionDocumentParseService;
import com.yf.exam.modules.qu.support.QuestionBoundaryHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class QuestionImportBatchProcessor {

    @Autowired
    private QuestionDocumentParseService documentParseService;

    @Autowired
    private QuestionAiParseService questionAiParseService;

    @Autowired
    private QuestionAiConfigProvider questionAiConfigProvider;

    public BatchProcessResult processBatch(String chunk, QuestionImportMode mode, QuestionParseReqDTO baseReq,
            String batchNo, QuestionImportBatchState state, boolean retrying) {
        if (StringUtils.isBlank(chunk) || QuestionBoundaryHelper.isIgnorableImportFragment(chunk)) {
            return BatchProcessResult.success(new ArrayList<>(), false);
        }

        state.markRunning(retrying ? QuestionImportBatchState.PHASE_RETRY : QuestionImportBatchState.PHASE_PARSE);

        String localText = documentParseService.normalizeLocally(chunk);
        if (StringUtils.isBlank(localText)) {
            if (QuestionBoundaryHelper.isIgnorableImportFragment(chunk)) {
                return BatchProcessResult.success(new ArrayList<>(), false);
            }
            throw new ServiceException("本地清洗后文本为空");
        }

        if (mode == QuestionImportMode.DEEP) {
            state.setPhase(QuestionImportBatchState.PHASE_DEEP_NORMALIZE);
            String deepText = questionAiParseService.normalizeSingleBatch(localText, batchNo);
            state.setPhase(retrying ? QuestionImportBatchState.PHASE_RETRY : QuestionImportBatchState.PHASE_PARSE);
            List<QuDetailDTO> questions = parseBatchText(deepText, baseReq, batchNo);
            return BatchProcessResult.success(questions, true);
        }

        if (mode == QuestionImportMode.FAST) {
            List<QuDetailDTO> questions = parseBatchText(localText, baseReq, batchNo);
            return BatchProcessResult.success(questions, false);
        }

        try {
            List<QuDetailDTO> questions = parseBatchText(localText, baseReq, batchNo);
            if (isBatchParseInsufficient(chunk, questions)) {
                throw new ServiceException("解析题数过少");
            }
            return BatchProcessResult.success(questions, false);
        } catch (Exception e) {
            if (QuestionBoundaryHelper.isIgnorableImportFragment(chunk)) {
                return BatchProcessResult.success(new ArrayList<>(), false);
            }
            try {
                state.setPhase(QuestionImportBatchState.PHASE_DEEP_NORMALIZE);
                String deepText = questionAiParseService.normalizeSingleBatch(localText, batchNo);
                state.setPhase(retrying ? QuestionImportBatchState.PHASE_RETRY : QuestionImportBatchState.PHASE_PARSE);
                List<QuDetailDTO> questions = parseBatchText(deepText, baseReq, batchNo);
                return BatchProcessResult.success(questions, true);
            } catch (Exception deepError) {
                throw new ServiceException("快速解析失败：" + safeMessage(e)
                        + "；深度清洗/解析仍失败：" + safeMessage(deepError));
            }
        }
    }

    private String safeMessage(Exception e) {
        String message = e == null ? "" : e.getMessage();
        return StringUtils.defaultIfBlank(message, "未知错误");
    }

    private List<QuDetailDTO> parseBatchText(String text, QuestionParseReqDTO baseReq, String batchNo) {
        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText(text);
        reqDTO.setRepoIds(baseReq.getRepoIds());
        reqDTO.setLevel(baseReq.getLevel());

        QuestionParseRespDTO respDTO = questionAiParseService.parseSingleBatch(reqDTO, batchNo);
        if (respDTO == null || CollectionUtils.isEmpty(respDTO.getQuestions())) {
            throw new ServiceException("AI未解析出任何试题");
        }
        return respDTO.getQuestions();
    }

    private boolean isBatchParseInsufficient(String chunk, List<QuDetailDTO> questions) {
        int expected = documentParseService.countQuestionBlocks(chunk);
        int minExpected = questionAiConfigProvider.getEffective().getParseFallbackMinExpected() == null
                ? 3
                : questionAiConfigProvider.getEffective().getParseFallbackMinExpected();
        if (expected < minExpected) {
            return false;
        }
        int actual = CollectionUtils.isEmpty(questions) ? 0 : questions.size();
        double ratio = questionAiConfigProvider.getEffective().getParseFallbackRatio() == null
                ? 0.3
                : questionAiConfigProvider.getEffective().getParseFallbackRatio();
        int threshold = Math.max(1, (int) Math.floor(expected * ratio));
        return actual < threshold;
    }

    public static class BatchProcessResult {
        private final List<QuDetailDTO> questions;
        private final boolean deepCleanUsed;

        private BatchProcessResult(List<QuDetailDTO> questions, boolean deepCleanUsed) {
            this.questions = questions == null ? new ArrayList<>() : questions;
            this.deepCleanUsed = deepCleanUsed;
        }

        public static BatchProcessResult success(List<QuDetailDTO> questions, boolean deepCleanUsed) {
            return new BatchProcessResult(questions, deepCleanUsed);
        }

        public List<QuDetailDTO> getQuestions() {
            return questions;
        }

        public boolean isDeepCleanUsed() {
            return deepCleanUsed;
        }
    }
}
