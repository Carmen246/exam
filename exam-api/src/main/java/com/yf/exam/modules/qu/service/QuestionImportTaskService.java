package com.yf.exam.modules.qu.service;

import com.yf.exam.modules.qu.dto.QuestionImportTaskCreateRespDTO;
import com.yf.exam.modules.qu.dto.QuestionImportTaskStatusRespDTO;
import com.yf.exam.modules.qu.entity.QuestionImportTask;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface QuestionImportTaskService {

    QuestionImportTaskCreateRespDTO createTask(MultipartFile file, MultipartFile answerFile, String text,
            List<String> repoIds, Integer level, String importMode, Boolean deepAiNormalize);

    QuestionImportTaskStatusRespDTO getTaskStatus(String taskId);

    QuestionImportTaskStatusRespDTO retryTask(String taskId);

    QuestionImportTaskStatusRespDTO retryTask(String taskId, Integer batchNo);

    void validateTaskReadyForImport(String taskId);

    QuestionImportTask requireImportReadyTask(String taskId);

    void processTask(String taskId);
}
