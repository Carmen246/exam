package com.yf.exam.modules.qu.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yf.exam.ability.upload.config.UploadConfig;
import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.dto.QuestionImportTaskCreateRespDTO;
import com.yf.exam.modules.qu.dto.QuestionImportTaskStatusRespDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.entity.QuestionImportTask;
import com.yf.exam.modules.qu.enums.QuestionImportTaskStatus;
import com.yf.exam.modules.qu.service.QuestionAiParseService;
import com.yf.exam.modules.qu.service.QuestionDocumentParseService;
import com.yf.exam.modules.qu.service.QuestionImportTaskService;
import com.yf.exam.modules.qu.support.ImportTaskProgressListener;
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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionImportTaskServiceImpl implements QuestionImportTaskService {

    private static final long TASK_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int PROGRESS_EXTRACTING = 5;
    private static final int PROGRESS_NORMALIZE_START = 5;
    private static final int PROGRESS_NORMALIZE_END = 40;
    private static final int PROGRESS_PARSE_START = 40;
    private static final int PROGRESS_PARSE_END = 95;

    private final Map<String, QuestionImportTask> tasks = new ConcurrentHashMap<>();

    @Autowired
    private UploadConfig uploadConfig;

    @Autowired
    private QuestionDocumentParseService documentParseService;

    @Autowired
    private QuestionAiParseService questionAiParseService;

    @Autowired
    @Qualifier("asyncExecutor")
    private ThreadPoolTaskExecutor asyncExecutor;

    @Override
    public QuestionImportTaskCreateRespDTO createTask(MultipartFile file, String text, List<String> repoIds,
            Integer level) {
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
            normalizeText(task);
            parseQuestions(task);
            finishTask(task);
        } catch (Exception e) {
            failTask(task, e);
        } finally {
            deleteTempFile(task.getTempFile());
            task.setTempFile(null);
        }
    }

    private void extractText(QuestionImportTask task) {
        updateTask(task, QuestionImportTaskStatus.EXTRACTING, PROGRESS_EXTRACTING, "正在提取文档文本");

        String rawText;
        if (task.getTempFile() != null) {
            rawText = documentParseService.parseText(task.getTempFile());
        } else {
            rawText = task.getInputText();
        }

        if (StringUtils.isBlank(rawText)) {
            throw new ServiceException("未提取到有效试题文本");
        }

        task.setRawText(rawText);
        if (task.getTempFile() != null) {
            task.setMessage("文档解析完成，共 " + rawText.length() + " 字，正在 AI 清洗文本");
            task.setProgress(PROGRESS_NORMALIZE_START);
        }
    }

    private void normalizeText(QuestionImportTask task) {
        updateTask(task, QuestionImportTaskStatus.NORMALIZING, PROGRESS_NORMALIZE_START, "正在AI清洗文本");

        QuestionNormalizeTextReqDTO reqDTO = new QuestionNormalizeTextReqDTO();
        reqDTO.setText(task.getRawText());

        ImportTaskProgressListener listener = (completed, total, message) -> {
            task.setTotalBatches(total);
            task.setCompletedBatches(completed);
            task.setMessage(message);
            task.setProgress(calcPhaseProgress(completed, total, PROGRESS_NORMALIZE_START, PROGRESS_NORMALIZE_END));
        };

        String normalizedText = questionAiParseService.normalizeText(reqDTO, listener).getNormalizedText();
        if (StringUtils.isBlank(normalizedText)) {
            throw new ServiceException("AI未返回清洗后的文本");
        }

        task.setNormalizedText(normalizedText);
        task.setMessage("AI 清洗完成，共 " + normalizedText.length() + " 字，正在解析试题");
    }

    private void parseQuestions(QuestionImportTask task) {
        updateTask(task, QuestionImportTaskStatus.PARSING, PROGRESS_PARSE_START, "正在AI解析试题");

        QuestionParseReqDTO reqDTO = new QuestionParseReqDTO();
        reqDTO.setText(task.getNormalizedText());
        reqDTO.setRepoIds(task.getRepoIds());
        reqDTO.setLevel(task.getLevel());

        ImportTaskProgressListener listener = (completed, total, message) -> {
            task.setTotalBatches(total);
            task.setCompletedBatches(completed);
            task.setMessage(message);
            task.setProgress(calcPhaseProgress(completed, total, PROGRESS_PARSE_START, PROGRESS_PARSE_END));
        };

        QuestionParseRespDTO respDTO = questionAiParseService.parseQuestions(reqDTO, listener);
        if (respDTO == null || CollectionUtils.isEmpty(respDTO.getQuestions())) {
            throw new ServiceException("AI未解析出任何试题");
        }

        task.setQuestions(respDTO.getQuestions());
    }

    private void finishTask(QuestionImportTask task) {
        task.setStatus(QuestionImportTaskStatus.COMPLETED);
        task.setProgress(100);
        task.setMessage("解析完成，共 " + task.getQuestions().size() + " 题");
        task.setCompletedBatches(task.getTotalBatches());
    }

    private void failTask(QuestionImportTask task, Exception e) {
        task.setStatus(QuestionImportTaskStatus.FAILED);
        String message = e instanceof ServiceException ? e.getMessage() : "导入任务处理失败：" + e.getMessage();
        task.setErrorMessage(message);
        task.setMessage(message);
    }

    private void updateTask(QuestionImportTask task, QuestionImportTaskStatus status, int progress, String message) {
        task.setStatus(status);
        task.setProgress(progress);
        task.setMessage(message);
        task.setTotalBatches(0);
        task.setCompletedBatches(0);
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

    private QuestionImportTaskStatusRespDTO toStatusResp(QuestionImportTask task) {
        QuestionImportTaskStatusRespDTO resp = new QuestionImportTaskStatusRespDTO();
        resp.setTaskId(task.getTaskId());
        resp.setStatus(task.getStatus().name());
        resp.setStatusLabel(task.getStatus().getLabel());
        resp.setProgress(task.getProgress());
        resp.setMessage(task.getMessage());
        resp.setTotalBatches(task.getTotalBatches());
        resp.setCompletedBatches(task.getCompletedBatches());
        resp.setFileName(task.getFileName());
        resp.setErrorMessage(task.getErrorMessage());

        if (task.getStatus() == QuestionImportTaskStatus.COMPLETED
                || task.getStatus() == QuestionImportTaskStatus.FAILED) {
            resp.setRawText(task.getRawText());
            resp.setNormalizedText(task.getNormalizedText());
            resp.setQuestions(task.getQuestions());
            resp.setCount(CollectionUtils.isEmpty(task.getQuestions()) ? 0 : task.getQuestions().size());
        }

        return resp;
    }
}
