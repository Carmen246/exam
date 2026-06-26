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

    private Integer maxTextLength = 20000;
}