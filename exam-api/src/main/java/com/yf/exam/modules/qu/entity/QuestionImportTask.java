package com.yf.exam.modules.qu.entity;

import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import com.yf.exam.modules.qu.enums.QuestionImportMode;
import com.yf.exam.modules.qu.enums.QuestionImportTaskStatus;
import lombok.Data;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 导入任务（内存存储）
 */
@Data
public class QuestionImportTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;

    private QuestionImportTaskStatus status = QuestionImportTaskStatus.PENDING;

    private Integer progress = 0;

    private String message;

    private Integer totalBatches = 0;

    private Integer completedBatches = 0;

    private Integer failedBatchCount = 0;

    private Integer deepCleanBatchCount = 0;

    private String fileName;

    private List<String> repoIds = new ArrayList<>();

    private Integer level = 1;

    private String importMode = QuestionImportMode.SMART.name();

    private String inputText;

    private File tempFile;

    private File answerTempFile;

    private String answerFileName;

    private List<String> mergeWarnings = new ArrayList<>();

    private Boolean ragflowUploadEnabled = false;

    private Boolean ragflowUploaded = false;

    private String ragflowDatasetId;

    private List<String> ragflowDocumentIds = new ArrayList<>();

    private String ragflowMessage;

    private String rawText;

    private String normalizedText;

    private List<QuDetailDTO> questions = new ArrayList<>();

    private List<QuestionImportBatchState> batchStates = new ArrayList<>();

    private String errorMessage;

    /** 创建任务的用户 ID */
    private String userId;

    private long createTime = System.currentTimeMillis();
}
