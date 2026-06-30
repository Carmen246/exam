package com.yf.exam.modules.qu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yf.exam.ability.upload.config.UploadConfig;
import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.config.QuestionAiProperties;
import com.yf.exam.modules.qu.dto.ImportBatchStatusDTO;
import com.yf.exam.modules.qu.dto.QuestionImportTaskCreateRespDTO;
import com.yf.exam.modules.qu.dto.QuestionImportTaskStatusRespDTO;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import com.yf.exam.modules.qu.entity.QuestionImportBatchState;
import com.yf.exam.modules.qu.entity.QuestionImportTask;
import com.yf.exam.modules.qu.enums.QuestionImportMode;
import com.yf.exam.modules.qu.enums.QuestionImportTaskStatus;
import com.yf.exam.modules.qu.service.QuestionAiParseService;
import com.yf.exam.modules.qu.service.QuestionDocumentParseService;
import com.yf.exam.modules.qu.service.QuestionImportTaskService;
import com.yf.exam.modules.qu.support.FillProgramBlankProcessor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class QuestionImportTaskServiceImpl implements QuestionImportTaskService {

    private static final long TASK_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int PROGRESS_EXTRACTING = 5;
    private static final int PROGRESS_PARSE_START = 10;
    private static final int PROGRESS_PARSE_END = 95;

    private final Map<String, QuestionImportTask> tasks = new ConcurrentHashMap<>();

    @Autowired
    private UploadConfig uploadConfig;

    @Autowired
    private QuestionDocumentParseService documentParseService;

    @Autowired
    private QuestionAiParseService questionAiParseService;

    @Autowired
    private QuestionImportBatchProcessor batchProcessor;

    @Autowired
    private QuestionAiProperties questionAiProperties;

    @Autowired
    @Qualifier("asyncExecutor")
    private ThreadPoolTaskExecutor asyncExecutor;

    @Override
    public QuestionImportTaskCreateRespDTO createTask(MultipartFile file, String text, List<String> repoIds,
            Integer level, String importMode, Boolean deepAiNormalize) {
        cleanupExpiredTasks();

        if (CollectionUtils.isEmpty(repoIds)) {
            throw new ServiceException("请至少选择一个题库");
        }

        boolean hasFile = file != null && !file.isEmpty();
        boolean hasText = StringUtils.isNotBlank(text);
        if (!hasFile && !hasText) {
            throw new ServiceException("请上传文档或粘贴试题文本");
        }

        String taskId = IdWorker.getIdStr();
        QuestionImportTask task = new QuestionImportTask();
        task.setTaskId(taskId);
        task.setRepoIds(new ArrayList<>(repoIds));
        task.setLevel(level == null ? 1 : level);
        task.setImportMode(resolveImportMode(importMode, deepAiNormalize).name());
        task.setStatus(QuestionImportTaskStatus.PENDING);
        task.setMessage(QuestionImportTaskStatus.PENDING.getLabel());

        if (hasFile) {
            task.setFileName(file.getOriginalFilename());
            task.setTempFile(saveTempFile(file, taskId));
        } else {
            task.setInputText(text.trim());
        }

        tasks.put(taskId, task);

        QuestionImportTaskCreateRespDTO resp = new QuestionImportTaskCreateRespDTO();
        resp.setTaskId(taskId);
        resp.setFileName(task.getFileName());
        asyncExecutor.execute(() -> processTask(taskId));
        return resp;
    }

    @Override
    public QuestionImportTaskStatusRespDTO getTaskStatus(String taskId) {
        cleanupExpiredTasks();
        QuestionImportTask task = tasks.get(taskId);
        if (task == null) {
            throw new ServiceException("导入任务不存在或已过期");
        }
        return toStatusResp(task);
    }

    @Override
    public void processTask(String taskId) {
        QuestionImportTask task = tasks.get(taskId);
        if (task == null) {
            return;
        }

        try {
            extractText(task);
            runBatchPipeline(task, false, null);
        } catch (Exception e) {
            failTask(task, e);
        } finally {
            deleteTempFile(task.getTempFile());
            task.setTempFile(null);
        }
    }

    @Override
    public QuestionImportTaskStatusRespDTO retryTask(String taskId) {
        return retryTask(taskId, null);
    }

    @Override
    public QuestionImportTaskStatusRespDTO retryTask(String taskId, Integer batchNo) {
        cleanupExpiredTasks();
        QuestionImportTask task = tasks.get(taskId);
        if (task == null) {
            throw new ServiceException("导入任务不存在或已过期");
        }
        if (task.getStatus() != QuestionImportTaskStatus.FAILED
                && task.getStatus() != QuestionImportTaskStatus.PARTIAL_COMPLETED) {
            throw new ServiceException("只有失败或部分完成的任务才能重试");
        }
        if (StringUtils.isBlank(task.getRawText())) {
            throw new ServiceException("文档未提取成功，请重新上传文件");
        }
        if (CollectionUtils.isEmpty(task.getBatchStates())) {
            throw new ServiceException("没有可重试的批次，请重新创建导入任务");
        }

        if (batchNo != null) {
            if (batchNo <= 0 || batchNo > task.getBatchStates().size()) {
                throw new ServiceException("批次序号无效：" + batchNo);
            }
            QuestionImportBatchState target = task.getBatchStates().get(batchNo - 1);
            if (!target.isFailed()) {
                throw new ServiceException("第" + batchNo + "批未失败，无需重试");
            }
        }

        task.setErrorMessage(null);
        task.setStatus(QuestionImportTaskStatus.PARSING);
        task.setProgress(PROGRESS_PARSE_START);
        task.setMessage(batchNo == null ? "正在重试失败批次..." : "正在重试第" + batchNo + "批...");

        final Integer retryBatchNo = batchNo;
        asyncExecutor.execute(() -> {
            try {
                runBatchPipeline(task, true, retryBatchNo);
            } catch (Exception e) {
                failTask(task, e);
            }
        });

        return toStatusResp(task);
    }

    private QuestionImportMode resolveImportMode(String importMode, Boolean deepAiNormalize) {
        if (Boolean.TRUE.equals(deepAiNormalize)) {
            return QuestionImportMode.DEEP;
        }
        return QuestionImportMode.from(importMode);
    }

    private void extractText(QuestionImportTask task) {
        updateTask(task, QuestionImportTaskStatus.EXTRACTING, PROGRESS_EXTRACTING, "正在提取文档文本");

        String rawText;
        if (task.getTempFile() != null) {
            rawText = documentParseService.extractRawText(task.getTempFile());
        } else {
            rawText = task.getInputText();
        }

        if (StringUtils.isBlank(rawText)) {
            throw new ServiceException("未提取到有效试题文本");
        }

        task.setRawText(rawText.trim());
        task.setMessage("文档提取完成，共 " + task.getRawText().length() + " 字，正在切分批次...");
    }

    private void runBatchPipeline(QuestionImportTask task, boolean retryFailedOnly, Integer retryBatchNo) {
        QuestionImportMode mode = QuestionImportMode.from(task.getImportMode());

        if (!retryFailedOnly || CollectionUtils.isEmpty(task.getBatchStates())) {
            List<String> chunks = questionAiParseService.splitParseBatches(task.getRawText());
            if (CollectionUtils.isEmpty(chunks)) {
                throw new ServiceException("未切分出有效试题批次");
            }
            List<QuestionImportBatchState> newBatchStates = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                newBatchStates.add(new QuestionImportBatchState(i, chunks.get(i)));
            }
            task.setBatchStates(newBatchStates);
        }

        final List<QuestionImportBatchState> batchStates = task.getBatchStates();
        List<Integer> workIndices = new ArrayList<>();
        if (retryFailedOnly) {
            if (retryBatchNo != null) {
                QuestionImportBatchState state = batchStates.get(retryBatchNo - 1);
                state.resetForRetry();
                workIndices.add(state.getBatchIndex());
            } else {
                for (QuestionImportBatchState state : batchStates) {
                    if (state.isFailed()) {
                        state.resetForRetry();
                        workIndices.add(state.getBatchIndex());
                    }
                }
            }
            if (workIndices.isEmpty()) {
                mergeAndFinalize(task);
                return;
            }
        } else {
            for (QuestionImportBatchState state : batchStates) {
                workIndices.add(state.getBatchIndex());
            }
        }

        int total = batchStates.size();
        task.setTotalBatches(total);
        task.setStatus(QuestionImportTaskStatus.PARSING);

        QuestionParseReqDTO baseReq = new QuestionParseReqDTO();
        baseReq.setRepoIds(task.getRepoIds());
        baseReq.setLevel(task.getLevel());

        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>(workIndices);
        AtomicInteger deepCleanCount = new AtomicInteger(countDeepClean(batchStates));

        int concurrency = Math.min(aiConcurrency(), workIndices.size());
        List<CompletableFuture<Void>> workers = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            workers.add(CompletableFuture.runAsync(() -> {
                Integer index;
                while ((index = queue.poll()) != null) {
                    QuestionImportBatchState state = batchStates.get(index);
                    String batchNo = String.valueOf(index + 1);
                    try {
                        QuestionImportBatchProcessor.BatchProcessResult result = batchProcessor.processBatch(
                                state.getChunkText(), mode, baseReq, batchNo, state, retryFailedOnly);
                        state.markSuccess(result.getQuestions(), result.isDeepCleanUsed());
                        if (result.isDeepCleanUsed()) {
                            deepCleanCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        String message = e instanceof ServiceException ? e.getMessage() : e.getMessage();
                        state.markFailed(message);
                    }
                    refreshBatchProgress(task, batchStates, deepCleanCount.get());
                }
            }, asyncExecutor));
        }

        CompletableFuture.allOf(workers.toArray(new CompletableFuture[0])).join();
        mergeAndFinalize(task);
    }

    private void refreshBatchProgress(QuestionImportTask task, List<QuestionImportBatchState> batchStates,
            int deepCleanCount) {
        int total = batchStates.size();
        int finished = 0;
        int failed = 0;
        for (QuestionImportBatchState state : batchStates) {
            if (state.isSuccess() || state.isFailed()) {
                finished++;
            }
            if (state.isFailed()) {
                failed++;
            }
        }

        task.setCompletedBatches(finished);
        task.setFailedBatchCount(failed);
        task.setDeepCleanBatchCount(deepCleanCount);
        task.setProgress(calcPhaseProgress(finished, total, PROGRESS_PARSE_START, PROGRESS_PARSE_END));
        task.setMessage(buildPipelineMessage(finished, total, deepCleanCount));
    }

    private String buildPipelineMessage(int finished, int total, int deepCleanCount) {
        StringBuilder message = new StringBuilder("正在解析试题：" + finished + "/" + total + " 批");
        if (deepCleanCount > 0) {
            message.append("，").append(deepCleanCount).append("批进入深度清洗");
        }
        return message.toString();
    }

    private void mergeAndFinalize(QuestionImportTask task) {
        List<QuDetailDTO> merged = new ArrayList<>();
        StringBuilder normalizedPreview = new StringBuilder();
        int failed = 0;
        int deepClean = 0;
        int success = 0;

        for (QuestionImportBatchState state : task.getBatchStates()) {
            if (state.isSuccess()) {
                success++;
                merged.addAll(state.getQuestions());
                if (state.isDeepCleanUsed()) {
                    deepClean++;
                }
                if (normalizedPreview.length() > 0) {
                    normalizedPreview.append("\n\n");
                }
                normalizedPreview.append(documentParseService.normalizeLocally(state.getChunkText()));
            } else if (state.isFailed()) {
                failed++;
            }
        }

        task.setQuestions(merged);
        task.setNormalizedText(normalizedPreview.toString());
        task.setFailedBatchCount(failed);
        task.setDeepCleanBatchCount(deepClean);
        task.setCompletedBatches(success + failed);

        if (success == 0) {
            failTask(task, new ServiceException("所有批次处理失败，请检查后重试"));
            return;
        }

        if (failed > 0) {
            task.setStatus(QuestionImportTaskStatus.PARTIAL_COMPLETED);
            task.setProgress(100);
            task.setMessage(buildPartialCompletedMessage(task, merged.size(), failed));
            task.setErrorMessage(null);
            return;
        }

        finishTask(task);
    }

    private int countDeepClean(List<QuestionImportBatchState> batchStates) {
        int count = 0;
        for (QuestionImportBatchState state : batchStates) {
            if (state.isDeepCleanUsed()) {
                count++;
            }
        }
        return count;
    }

    private int aiConcurrency() {
        Integer configured = questionAiProperties.getAiConcurrency();
        if (configured == null || configured <= 0) {
            configured = questionAiProperties.getNormalizeConcurrency();
        }
        if (configured == null || configured <= 0) {
            return 3;
        }
        return Math.min(configured, 8);
    }

    private void finishTask(QuestionImportTask task) {
        task.setStatus(QuestionImportTaskStatus.COMPLETED);
        task.setProgress(100);
        task.setMessage("解析完成，共 " + task.getQuestions().size() + " 题");
        task.setFailedBatchCount(0);
        task.setErrorMessage(null);
    }

    private void failTask(QuestionImportTask task, Exception e) {
        task.setStatus(QuestionImportTaskStatus.FAILED);
        String message = e instanceof ServiceException ? e.getMessage() : "导入任务处理失败：" + e.getMessage();
        String failedNos = formatFailedBatchNos(task);
        if (StringUtils.isNotBlank(failedNos)) {
            message = message + "（失败批次：" + failedNos + "）";
        }
        task.setErrorMessage(message);
        task.setMessage(message);
        mergeQuestionsFromBatches(task);
    }

    private void mergeQuestionsFromBatches(QuestionImportTask task) {
        if (!CollectionUtils.isEmpty(task.getQuestions())) {
            return;
        }
        List<QuDetailDTO> merged = new ArrayList<>();
        if (!CollectionUtils.isEmpty(task.getBatchStates())) {
            for (QuestionImportBatchState state : task.getBatchStates()) {
                if (state.isSuccess()) {
                    merged.addAll(state.getQuestions());
                }
            }
        }
        task.setQuestions(merged);
    }

    private void updateTask(QuestionImportTask task, QuestionImportTaskStatus status, int progress, String message) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setMessage(message);
    }

    private int calcPhaseProgress(int completed, int total, int start, int end) {
        if (total <= 0) {
            return start;
        }
        int range = end - start;
        return start + (int) Math.round((completed * 1.0 / total) * range);
    }

    private File saveTempFile(MultipartFile file, String taskId) {
        try {
            String ext = FilenameUtils.getExtension(file.getOriginalFilename());
            if (StringUtils.isBlank(ext)) {
                throw new ServiceException("无法识别文件类型");
            }

            File dir = resolveAiImportDir();
            File dest = new File(dir, taskId + "." + ext);
            file.transferTo(dest);
            return dest;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("保存上传文件失败：" + e.getMessage());
        }
    }

    private File resolveAiImportDir() {
        File dir = new File(resolveUploadBaseDir(), "ai-import");
        if (!ensureWritableDir(dir)) {
            throw new ServiceException("创建临时目录失败：" + dir.getAbsolutePath());
        }
        return dir;
    }

    private File resolveUploadBaseDir() {
        String configuredDir = uploadConfig.getDir();
        if (StringUtils.isBlank(configuredDir)) {
            return defaultUploadBaseDir();
        }

        String normalized = configuredDir.trim();
        File dir = new File(normalized);
        if (!dir.isAbsolute()) {
            normalized = normalized.replaceFirst("^/+", "");
            dir = new File(System.getProperty("user.home"), normalized);
        }

        if (ensureWritableDir(dir)) {
            return dir;
        }
        return defaultUploadBaseDir();
    }

    private File defaultUploadBaseDir() {
        File dir = new File(System.getProperty("user.home"), "yfexam-upload");
        if (!ensureWritableDir(dir)) {
            throw new ServiceException("创建上传目录失败：" + dir.getAbsolutePath());
        }
        return dir;
    }

    private boolean ensureWritableDir(File dir) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            return dir.isDirectory() && dir.canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private void cleanupExpiredTasks() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, QuestionImportTask>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, QuestionImportTask> entry = iterator.next();
            QuestionImportTask task = entry.getValue();
            if (now - task.getCreateTime() > TASK_TTL_MS) {
                deleteTempFile(task.getTempFile());
                iterator.remove();
            }
        }
    }

    private String buildPartialCompletedMessage(QuestionImportTask task, int questionCount, int failedCount) {
        String failedNos = formatFailedBatchNos(task);
        if (StringUtils.isNotBlank(failedNos)) {
            return "成功识别 " + questionCount + " 题，失败 " + failedCount + " 批（" + failedNos + "），可重试失败批次";
        }
        return "成功识别 " + questionCount + " 题，失败 " + failedCount + " 批，可重试失败批次";
    }

    private String formatFailedBatchNos(QuestionImportTask task) {
        if (CollectionUtils.isEmpty(task.getBatchStates())) {
            return "";
        }
        List<String> nos = new ArrayList<>();
        for (QuestionImportBatchState state : task.getBatchStates()) {
            if (state.isFailed()) {
                nos.add(String.valueOf(state.getBatchIndex() + 1));
            }
        }
        return String.join("、", nos);
    }

    private List<ImportBatchStatusDTO> toBatchStatusList(QuestionImportTask task) {
        if (CollectionUtils.isEmpty(task.getBatchStates())) {
            return new ArrayList<>();
        }
        List<ImportBatchStatusDTO> batches = new ArrayList<>();
        for (QuestionImportBatchState state : task.getBatchStates()) {
            ImportBatchStatusDTO dto = new ImportBatchStatusDTO();
            dto.setBatchNo(state.getBatchIndex() + 1);
            dto.setPhase(state.getPhase());
            dto.setStatus(state.getStatus());
            dto.setQuestionCount(state.getQuestionCount());
            dto.setErrorMessage(state.getErrorMessage());
            dto.setPreviewText(FillProgramBlankProcessor.hideFillMarkersForDisplay(state.getPreviewText()));
            batches.add(dto);
        }
        return batches;
    }

    private QuestionImportTaskStatusRespDTO toStatusResp(QuestionImportTask task) {
        QuestionImportTaskStatusRespDTO resp = new QuestionImportTaskStatusRespDTO();
        resp.setTaskId(task.getTaskId());
        resp.setStatus(task.getStatus().name());
        resp.setStatusLabel(task.getStatus().getLabel());
        resp.setProgress(task.getProgress());
        resp.setMessage(task.getMessage());
        resp.setTotalBatches(task.getTotalBatches());
        resp.setCompletedBatches(task.getCompletedBatches());
        resp.setFailedBatchCount(task.getFailedBatchCount());
        resp.setDeepCleanBatchCount(task.getDeepCleanBatchCount());
        resp.setImportMode(task.getImportMode());
        resp.setFileName(task.getFileName());
        resp.setErrorMessage(task.getErrorMessage());
        resp.setBatches(toBatchStatusList(task));

        boolean exposeResult = task.getStatus() == QuestionImportTaskStatus.COMPLETED
                || task.getStatus() == QuestionImportTaskStatus.PARTIAL_COMPLETED
                || task.getStatus() == QuestionImportTaskStatus.FAILED;

        if (exposeResult) {
            resp.setRawText(FillProgramBlankProcessor.hideFillMarkersForDisplay(task.getRawText()));
            resp.setNormalizedText(FillProgramBlankProcessor.hideFillMarkersForDisplay(task.getNormalizedText()));
            resp.setQuestions(task.getQuestions());
            resp.setCount(CollectionUtils.isEmpty(task.getQuestions()) ? 0 : task.getQuestions().size());
        }

        return resp;
    }
}
