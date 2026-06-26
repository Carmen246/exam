package com.yf.exam.modules.qu.controller;

import com.yf.exam.core.api.ApiRest;
import com.yf.exam.core.api.controller.BaseController;
import com.yf.exam.modules.qu.dto.QuestionParseTextRespDTO;
import com.yf.exam.modules.qu.service.QuestionDocumentParseService;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.io.FilenameUtils;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.yf.exam.modules.qu.service.QuestionAiParseService;
import com.yf.exam.modules.qu.dto.QuestionParseReqDTO;
import com.yf.exam.modules.qu.dto.QuestionParseRespDTO;
import com.yf.exam.modules.qu.dto.QuestionImportReqDTO;
import com.yf.exam.modules.qu.dto.QuestionImportRespDTO;
@RestController
@RequestMapping("/exam/api/qu")
public class QuestionAiImportController extends BaseController {

    @Autowired
    private QuestionDocumentParseService documentParseService;

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
