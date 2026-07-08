package com.yf.exam.modules.sys.config.controller;

import com.yf.exam.core.api.ApiRest;
import com.yf.exam.core.api.controller.BaseController;
import com.yf.exam.core.api.dto.BaseIdRespDTO;
import com.yf.exam.modules.qu.service.QuestionAiParseService;
import com.yf.exam.modules.sys.config.dto.SysAiConfigDTO;
import com.yf.exam.modules.sys.config.entity.SysAiConfig;
import com.yf.exam.modules.sys.config.service.SysAiConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = {"AI配置"})
@RestController
@RequestMapping("/exam/api/sys/ai-config")
public class SysAiConfigController extends BaseController {

    @Autowired
    private SysAiConfigService sysAiConfigService;

    @Autowired
    private QuestionAiParseService questionAiParseService;

    @RequiresRoles("sa")
    @ApiOperation(value = "查询 AI 配置")
    @RequestMapping(value = "/detail", method = {RequestMethod.POST})
    public ApiRest<SysAiConfigDTO> detail() {
        return success(sysAiConfigService.findDetail());
    }

    @RequiresRoles("sa")
    @ApiOperation(value = "保存 AI 配置")
    @RequestMapping(value = "/save", method = {RequestMethod.POST})
    public ApiRest<BaseIdRespDTO> save(@RequestBody SysAiConfigDTO reqDTO) {
        sysAiConfigService.saveConfig(reqDTO);
        return success(new BaseIdRespDTO(SysAiConfig.DEFAULT_ID));
    }

    @RequiresRoles("sa")
    @ApiOperation(value = "测试 AI 连通性")
    @RequestMapping(value = "/ping", method = {RequestMethod.POST})
    public ApiRest<String> ping(@RequestBody(required = false) SysAiConfigDTO reqDTO) {
        return success(questionAiParseService.pingAi(reqDTO));
    }
}
