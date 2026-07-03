package com.yf.exam.modules.qu.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yf.exam.core.exception.ServiceException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * RAGFlow returns business errors with HTTP 200, so parse the body before treating it as OpenAI output.
 */
class RagflowChatLanguageModel implements ChatLanguageModel {

    private static final int MAX_ERROR_LENGTH = 260;

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final RestTemplate restTemplate;

    RagflowChatLanguageModel(String baseUrl, String apiKey, String modelName, Integer timeoutSeconds) {
        this(baseUrl, apiKey, modelName, createRestTemplate(timeoutSeconds));
    }

    RagflowChatLanguageModel(String baseUrl, String apiKey, String modelName, RestTemplate restTemplate) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.modelName = StringUtils.defaultIfBlank(modelName, "model");
        this.restTemplate = restTemplate;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(chatCompletionsUrl(),
                    new HttpEntity<>(buildRequestBody(messages), buildHeaders()), String.class);
            return parseResponse(response.getBody());
        } catch (ServiceException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            throw new ServiceException("RAGFlow AI调用失败：" + extractErrorMessage(e.getResponseBodyAsString(),
                    e.getMessage()));
        } catch (Exception e) {
            throw new ServiceException("RAGFlow AI调用失败：" + AiCallErrorSupport.toUserMessage(e));
        }
    }

    private String buildRequestBody(List<ChatMessage> messages) {
        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("stream", false);
        body.put("temperature", 0.1);
        body.put("max_tokens", 8000);
        body.put("messages", toOpenAiMessages(messages));
        return body.toJSONString();
    }

    private JSONArray toOpenAiMessages(List<ChatMessage> messages) {
        JSONArray array = new JSONArray();
        if (messages == null) {
            return array;
        }
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("role", roleOf(message));
            item.put("content", StringUtils.defaultString(message.text()));
            array.add(item);
        }
        return array;
    }

    private String roleOf(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return "system";
        }
        if (message instanceof UserMessage) {
            return "user";
        }
        if (message instanceof AiMessage) {
            return "assistant";
        }
        return "user";
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private Response<AiMessage> parseResponse(String body) {
        if (StringUtils.isBlank(body)) {
            throw new ServiceException("RAGFlow AI调用失败：返回内容为空");
        }

        JSONObject json;
        try {
            json = JSON.parseObject(body);
        } catch (Exception e) {
            throw new ServiceException("RAGFlow AI调用失败：返回内容不是合法JSON：" + abbreviate(body));
        }

        Integer code = json.getInteger("code");
        if (code != null && code != 0) {
            throw new ServiceException("RAGFlow AI调用失败：" + extractErrorMessage(body, body));
        }

        JSONArray choices = json.getJSONArray("choices");
        if ((choices == null || choices.isEmpty()) && json.getJSONObject("data") != null) {
            choices = json.getJSONObject("data").getJSONArray("choices");
        }
        if (choices == null || choices.isEmpty()) {
            throw new ServiceException("RAGFlow AI调用失败：返回内容缺少 choices：" + abbreviate(body));
        }

        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice == null ? null : choice.getJSONObject("message");
        String content = message == null ? null : message.getString("content");
        if (StringUtils.isBlank(content) && choice != null) {
            content = choice.getString("content");
        }
        if (StringUtils.isBlank(content)) {
            throw new ServiceException("RAGFlow AI调用失败：返回内容为空：" + abbreviate(body));
        }

        return Response.from(AiMessage.from(content), tokenUsageFrom(json.getJSONObject("usage")),
                finishReasonFrom(choice == null ? null : choice.getString("finish_reason")));
    }

    private TokenUsage tokenUsageFrom(JSONObject usage) {
        if (usage == null) {
            return null;
        }
        return new TokenUsage(usage.getInteger("prompt_tokens"), usage.getInteger("completion_tokens"),
                usage.getInteger("total_tokens"));
    }

    private FinishReason finishReasonFrom(String reason) {
        if (StringUtils.isBlank(reason)) {
            return null;
        }
        if ("stop".equals(reason)) {
            return FinishReason.STOP;
        }
        if ("length".equals(reason)) {
            return FinishReason.LENGTH;
        }
        if ("tool_calls".equals(reason) || "function_call".equals(reason)) {
            return FinishReason.TOOL_EXECUTION;
        }
        if ("content_filter".equals(reason)) {
            return FinishReason.CONTENT_FILTER;
        }
        return null;
    }

    private String extractErrorMessage(String body, String fallback) {
        if (StringUtils.isNotBlank(body)) {
            try {
                JSONObject json = JSON.parseObject(body);
                String message = json.getString("message");
                if (StringUtils.isNotBlank(message)) {
                    return abbreviate(message);
                }
            } catch (Exception ignored) {
                return abbreviate(body);
            }
        }
        return abbreviate(StringUtils.defaultIfBlank(fallback, "未知错误"));
    }

    private String chatCompletionsUrl() {
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/chat/completions";
    }

    private static RestTemplate createRestTemplate(Integer timeoutSeconds) {
        int timeout = Math.max(1, timeoutSeconds == null ? 60 : timeoutSeconds) * 1000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private static String trimTrailingSlash(String url) {
        String value = StringUtils.trimToEmpty(url);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String abbreviate(String value) {
        return StringUtils.abbreviate(StringUtils.trimToEmpty(value), MAX_ERROR_LENGTH);
    }
}
