package com.yf.exam.modules.qu.service;

import com.yf.exam.modules.qu.dto.QuestionImportTaskCreateRespDTO;
import com.yf.exam.modules.qu.dto.QuestionImportTaskStatusRespDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface QuestionImportTaskService {

    QuestionImportTaskCreateRespDTO createTask(MultipartFile file, String text, List<String> repoIds, Integer level);

    QuestionImportTaskStatusRespDTO getTaskStatus(String taskId);

    QuestionImportTaskStatusRespDTO retryTask(String taskId);

    void processTask(String taskId);
}
