package com.yf.exam.modules.qu.controller;

import com.yf.exam.core.api.ApiRest;
import com.yf.exam.core.api.controller.BaseController;
import com.yf.exam.modules.qu.dto.QuestionParseTextRespDTO;
import com.yf.exam.modules.qu.service.QuestionDocumentParseService;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.io.FilenameUtils;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import com.yf.exam.modules.qu.dto.QuestionImportTaskCreateRespDTO;
import com.yf.exam.modules.qu.dto.QuestionImportTaskStatusRespDTO;
import com.yf.exam.modules.qu.service.QuestionImportTaskService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import com.yf.exam.modules.qu.service.QuestionAiParseService;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.dto.QuestionImportReqDTO;
import com.yf.exam.modules.qu.dto.QuestionImportRespDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextReqDTO;
import com.yf.exam.modules.qu.dto.QuestionNormalizeTextRespDTO;
@RestController
@RequestMapping("/exam/api/qu")
public class QuestionAiImportController extends BaseController {

    @Autowired
    private QuestionDocumentParseService documentParseService;

    @Autowired
    private QuestionImportTaskService questionImportTaskService;

    @RequiresRoles("sa")
    @ApiOperation(value = "创建AI导入异步任务")
    @PostMapping("/import-task")
    public ApiRest<QuestionImportTaskCreateRespDTO> createImportTask(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "answerFile", required = false) MultipartFile answerFile,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam("repoIds") List<String> repoIds,
            @RequestParam(value = "level", defaultValue = "1") Integer level,
            @RequestParam(value = "importMode", defaultValue = "SMART") String importMode,
            @RequestParam(value = "deepAiNormalize", required = false) Boolean deepAiNormalize) {
        QuestionImportTaskCreateRespDTO resp = questionImportTaskService.createTask(file, answerFile, text, repoIds,
                level, importMode, deepAiNormalize);
        return success(resp);
    }

    @RequiresRoles("sa")
    @ApiOperation(value = "查询AI导入任务进度")
    @GetMapping("/import-task/{taskId}")
    public ApiRest<QuestionImportTaskStatusRespDTO> getImportTaskStatus(@PathVariable("taskId") String taskId) {
        return success(questionImportTaskService.getTaskStatus(taskId));
    }

    @RequiresRoles("sa")
    @ApiOperation(value = "重试失败的AI导入任务")
    @PostMapping("/import-task/{taskId}/retry")
    public ApiRest<QuestionImportTaskStatusRespDTO> retryImportTask(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "batchNo", required = false) Integer batchNo) {
        return success(questionImportTaskService.retryTask(taskId, batchNo));
    }

    @RequiresRoles("sa")
    @PostMapping("/parse-text")
    public ApiRest<QuestionParseTextRespDTO> parseText(@RequestParam("file") MultipartFile file) {
        String rawText = documentParseService.parseText(file);

        QuestionParseTextRespDTO resp = new QuestionParseTextRespDTO();
        resp.setFileName(file.getOriginalFilename());
        resp.setFileType(FilenameUtils.getExtension(file.getOriginalFilename()));
        resp.setRawText(rawText);
        resp.setCharCount(rawText.length());
        resp.setPreviewText(rawText.length() > 500 ? rawText.substring(0, 500) : rawText);

        return success(resp);
    }
    
    @RequiresRoles("sa")
    @ApiOperation(value = "AI清洗试题文本")
    @RequestMapping(value = "/normalize-text", method = {RequestMethod.POST})
    public ApiRest<QuestionNormalizeTextRespDTO> normalizeText(@RequestBody QuestionNormalizeTextReqDTO reqDTO) {
        QuestionNormalizeTextRespDTO respDTO = questionAiParseService.normalizeText(reqDTO);
        return super.success(respDTO);
    }

    @RequiresRoles("sa")
    @ApiOperation(value = "测试AI后端连通性")
    @GetMapping("/ai/ping")
    public ApiRest<String> pingAi() {
        return success(questionAiParseService.pingAi());
    }

    @Autowired
    private QuestionAiParseService questionAiParseService;
    @RequiresRoles("sa")
    @ApiOperation(value = "AI解析试题文本")
    @RequestMapping(value = "/parse-questions", method = {RequestMethod.POST})
    public ApiRest<QuestionParseRespDTO> parseQuestions(@RequestBody QuestionParseReqDTO reqDTO) {
        QuestionParseRespDTO respDTO = questionAiParseService.parseQuestions(reqDTO);
        return super.success(respDTO);
    }
    @RequiresRoles("sa")
    @ApiOperation(value = "AI确认导入试题")
    @RequestMapping(value = "/confirm-import", method = {RequestMethod.POST})
    public ApiRest<QuestionImportRespDTO> confirmImport(@RequestBody QuestionImportReqDTO reqDTO) {
        QuestionImportRespDTO respDTO = questionAiParseService.importQuestions(reqDTO);
        return super.success(respDTO);
    }
}
