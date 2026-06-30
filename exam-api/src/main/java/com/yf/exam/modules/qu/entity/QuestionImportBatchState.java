package com.yf.exam.modules.qu.entity;

import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import org.apache.commons.lang3.StringUtils;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入任务单批处理状态
 */
@Data
public class QuestionImportBatchState implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public static final String PHASE_PARSE = "PARSE";
    public static final String PHASE_DEEP_NORMALIZE = "DEEP_NORMALIZE";
    public static final String PHASE_RETRY = "RETRY";

    private int batchIndex;

    private String chunkText;

    private String phase = PHASE_PARSE;

    private String status = STATUS_PENDING;

    private String errorMessage;

    private Integer questionCount = 0;

    private String previewText;

    private boolean deepCleanUsed;

    private List<QuDetailDTO> questions = new ArrayList<>();

    public QuestionImportBatchState() {
    }

    public QuestionImportBatchState(int batchIndex, String chunkText) {
        this.batchIndex = batchIndex;
        this.chunkText = chunkText;
        this.previewText = buildPreviewText(chunkText);
    }

    public void markRunning(String phase) {
        this.status = STATUS_RUNNING;
        this.phase = StringUtils.defaultIfBlank(phase, PHASE_PARSE);
        this.errorMessage = null;
    }

    public void markSuccess(List<QuDetailDTO> parsedQuestions, boolean deepCleanUsed) {
        this.status = STATUS_SUCCESS;
        this.errorMessage = null;
        this.deepCleanUsed = deepCleanUsed;
        this.questions = parsedQuestions == null ? new ArrayList<>() : new ArrayList<>(parsedQuestions);
        this.questionCount = this.questions.size();
    }

    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.errorMessage = errorMessage;
        this.questions = new ArrayList<>();
        this.questionCount = 0;
    }

    public void resetForRetry() {
        this.status = STATUS_PENDING;
        this.phase = PHASE_RETRY;
        this.errorMessage = null;
        this.deepCleanUsed = false;
        this.questions = new ArrayList<>();
        this.questionCount = 0;
    }

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }

    public static String buildPreviewText(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 120) + "...";
    }
}
