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
}
