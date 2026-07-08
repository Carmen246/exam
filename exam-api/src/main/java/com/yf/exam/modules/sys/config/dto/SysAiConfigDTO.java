package com.yf.exam.modules.sys.config.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(value = "AI配置", description = "AI 接口配置")
public class SysAiConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String MASKED_SECRET = "******";

    @ApiModelProperty(value = "ID")
    private String id;

    @ApiModelProperty(value = "启用数据库配置（覆盖 application.yml）")
    private Boolean enabled;

    @ApiModelProperty(value = "提供方：openai / ragflow")
    private String provider;

    @ApiModelProperty(value = "API 地址")
    private String baseUrl;

    @ApiModelProperty(value = "API Key（留空表示不修改已保存的密钥）")
    private String apiKey;

    @ApiModelProperty(value = "是否已配置 API Key")
    private Boolean apiKeyConfigured;

    @ApiModelProperty(value = "RAGFlow 聊天助手 ID")
    private String chatId;

    @ApiModelProperty(value = "模型名称")
    private String modelName;

    @ApiModelProperty(value = "请求超时（秒）")
    private Integer timeoutSeconds;

    @ApiModelProperty(value = "RAGFlow 知识库 API 地址")
    private String ragflowBaseUrl;

    @ApiModelProperty(value = "RAGFlow API Key（留空表示不修改已保存的密钥）")
    private String ragflowApiKey;

    @ApiModelProperty(value = "是否已配置 RAGFlow API Key")
    private Boolean ragflowApiKeyConfigured;

    @ApiModelProperty(value = "RAGFlow 知识库 ID")
    private String ragflowDatasetId;

    @ApiModelProperty(value = "RAGFlow 知识库名称")
    private String ragflowDatasetName;

    @ApiModelProperty(value = "上传试卷时同步写入 RAGFlow 知识库")
    private Boolean ragflowAutoUpload;

    @ApiModelProperty(value = "RAGFlow 上传失败时中断 AI 导入")
    private Boolean ragflowUploadFailFast;
}
