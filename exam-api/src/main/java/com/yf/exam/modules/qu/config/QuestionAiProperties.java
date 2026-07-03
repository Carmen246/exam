package com.yf.exam.modules.qu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "exam.ai")
public class QuestionAiProperties {

    /** openai=直连 OpenAI 兼容 API；ragflow=对接 RAGFlow 聊天助手（方式 A） */
    private String provider = "openai";

    private String baseUrl;

    private String apiKey;

    /** RAGFlow 聊天助手 ID（provider=ragflow 时必填） */
    private String chatId;

    /** RAGFlow knowledge API base URL, independent from the parser model URL. */
    private String ragflowBaseUrl;

    /** RAGFlow API key, independent from the parser model API key. */
    private String ragflowApiKey;

    /** RAGFlow 知识库 ID；为空时可通过知识库列表接口自动获取 */
    private String ragflowDatasetId;

    /** RAGFlow 知识库名称；datasetId 为空时按名称从列表接口匹配 */
    private String ragflowDatasetName;

    /** RAGFlow 知识库列表接口每页数量 */
    private Integer ragflowDatasetPageSize = 10;

    /** 上传试卷文档后，是否同步写入 RAGFlow 知识库并触发解析 */
    private Boolean ragflowAutoUpload = false;

    /** RAGFlow 知识库上传失败时，是否中断本次 AI 导入 */
    private Boolean ragflowUploadFailFast = false;

    /** RAGFlow 上传/触发解析接口超时时间 */
    private Integer ragflowUploadTimeoutSeconds = 120;

    private String modelName = "deepseek-v4-flash";

    private Integer timeoutSeconds = 60;

    /** 解析阶段单批最大字符数上限（与清洗批次独立） */
    private Integer maxTextLength = 20000;

    /** 清洗单批最大字符数 */
    private Integer normalizeBatchLength = 10000;

    /** 清洗单批最大题数 */
    private Integer normalizeBatchQuestionCount = 20;

    /** 清洗并发批次数 */
    private Integer normalizeConcurrency = 3;

    /** 快速解析时，识别题数低于预期的比例则自动深度清洗 */
    private Double parseFallbackRatio = 0.3;

    /** 触发比例兜底的最小预期题数 */
    private Integer parseFallbackMinExpected = 3;

    /** 批次流水线统一 AI 并发数（清洗+解析共享） */
    private Integer aiConcurrency = 2;
}
