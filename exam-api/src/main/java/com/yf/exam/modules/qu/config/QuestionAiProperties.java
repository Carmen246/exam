package com.yf.exam.modules.qu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "exam.ai")
public class QuestionAiProperties {

    private String baseUrl;

    private String apiKey;

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
    private Integer aiConcurrency = 3;
}
