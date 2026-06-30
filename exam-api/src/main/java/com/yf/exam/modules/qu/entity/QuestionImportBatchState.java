package com.yf.exam.modules.qu.entity;

import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
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
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private int batchIndex;

    private String chunkText;

    private String status = STATUS_PENDING;

    private String errorMessage;

    private boolean deepCleanUsed;

    private List<QuDetailDTO> questions = new ArrayList<>();

    public QuestionImportBatchState() {
    }

    public QuestionImportBatchState(int batchIndex, String chunkText) {
        this.batchIndex = batchIndex;
        this.chunkText = chunkText;
    }

    public void markSuccess(List<QuDetailDTO> parsedQuestions, boolean deepCleanUsed) {
        this.status = STATUS_SUCCESS;
        this.errorMessage = null;
        this.deepCleanUsed = deepCleanUsed;
        this.questions = parsedQuestions == null ? new ArrayList<>() : new ArrayList<>(parsedQuestions);
    }

    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.errorMessage = errorMessage;
        this.questions = new ArrayList<>();
    }

    public void resetForRetry() {
        this.status = STATUS_PENDING;
        this.errorMessage = null;
        this.deepCleanUsed = false;
        this.questions = new ArrayList<>();
    }

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }
}
