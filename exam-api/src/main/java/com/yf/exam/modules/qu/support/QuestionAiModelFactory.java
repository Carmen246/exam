package com.yf.exam.modules.qu.support;

import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.config.QuestionAiProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 统一创建 AI 对话模型，支持直连 OpenAI 兼容 API 与 RAGFlow。
 */
@Component
public class QuestionAiModelFactory {

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_RAGFLOW = "ragflow";

    public ChatLanguageModel create(QuestionAiProperties properties) {
        if (properties == null) {
            throw new ServiceException("AI 配置为空");
        }
        if (StringUtils.isBlank(properties.getApiKey())) {
            throw new ServiceException(resolveMissingApiKeyMessage(properties));
        }
        if (StringUtils.isBlank(properties.getBaseUrl())) {
            throw new ServiceException("未配置 exam.ai.base-url");
        }

        String provider = normalizeProvider(properties.getProvider());
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .temperature(0.1)
                .maxTokens(8000)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds() == null
                        ? 60
                        : properties.getTimeoutSeconds()))
                .maxRetries(1);

        if (PROVIDER_RAGFLOW.equals(provider)) {
            if (StringUtils.isBlank(properties.getChatId())) {
                throw new ServiceException("RAGFlow 模式需配置 exam.ai.chat-id（聊天助手 ID）");
            }
            return new RagflowChatLanguageModel(buildRagflowOpenAiBaseUrl(properties), properties.getApiKey(),
                    StringUtils.defaultIfBlank(properties.getModelName(), "model"), properties.getTimeoutSeconds());
        } else {
            builder.baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                    .modelName(properties.getModelName())
                    .responseFormat("json_object");
        }

        return builder.build();
    }

    public boolean isRagflow(QuestionAiProperties properties) {
        return properties != null && PROVIDER_RAGFLOW.equalsIgnoreCase(normalizeProvider(properties.getProvider()));
    }

    private String buildRagflowOpenAiBaseUrl(QuestionAiProperties properties) {
        String root = trimTrailingSlash(properties.getBaseUrl());
        String chatId = properties.getChatId().trim();
        if (isRagflowOpenAiBaseUrl(root)) {
            return root;
        }
        if (isRagflowOpenAiBasePath(root)) {
            return root + "/" + chatId;
        }

        return root + "/api/v1/openai/" + chatId;
    }

    private boolean isRagflowOpenAiBaseUrl(String url) {
        return StringUtils.contains(url, "/api/v1/openai/")
                || StringUtils.contains(url, "/api/v1/chats_openai/");
    }

    private boolean isRagflowOpenAiBasePath(String url) {
        return url.endsWith("/api/v1/openai") || url.endsWith("/api/v1/chats_openai");
    }

    private String normalizeProvider(String provider) {
        if (StringUtils.isBlank(provider)) {
            return PROVIDER_OPENAI;
        }
        return provider.trim().toLowerCase();
    }

    private String trimTrailingSlash(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String resolveMissingApiKeyMessage(QuestionAiProperties properties) {
        if (isRagflow(properties)) {
            return "未配置 RAGFLOW_API_KEY（exam.ai.api-key）";
        }
        return "未配置 DEEPSEEK_API_KEY（exam.ai.api-key）";
    }
}
