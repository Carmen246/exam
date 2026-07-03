package com.yf.exam.modules.qu.support;

import com.yf.exam.core.exception.ServiceException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagflowChatLanguageModelTest {

    @Test
    void throwsRagflowMessageWhenHttp200ContainsErrorCode() {
        StubRestTemplate restTemplate = new StubRestTemplate(
                "{\"code\":400,\"data\":null,\"message\":\"EmbeddingError('Failed to connect to Ollama')\"}");
        RagflowChatLanguageModel model = new RagflowChatLanguageModel(
                "http://localhost:9380/api/v1/openai/chat-id", "test-key", "model", restTemplate);

        ServiceException error = assertThrows(ServiceException.class,
                () -> model.generate(Collections.singletonList(UserMessage.from("ping"))));

        assertTrue(error.getMessage().contains("RAGFlow AI调用失败"));
        assertTrue(error.getMessage().contains("Failed to connect to Ollama"));
    }

    @Test
    void readsTextFromOpenAiCompatibleResponse() {
        StubRestTemplate restTemplate = new StubRestTemplate(
                "{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}");
        RagflowChatLanguageModel model = new RagflowChatLanguageModel(
                "http://localhost:9380/api/v1/openai/chat-id", "test-key", "model", restTemplate);

        Response<AiMessage> response = model.generate(Collections.singletonList(UserMessage.from("ping")));

        assertEquals("OK", response.content().text());
        assertEquals("http://localhost:9380/api/v1/openai/chat-id/chat/completions", restTemplate.url);
        assertEquals("Bearer test-key", restTemplate.authorization);
        assertTrue(restTemplate.requestBody.contains("\"content\":\"ping\""));
    }

    private static class StubRestTemplate extends RestTemplate {

        private final String responseBody;
        private String url;
        private String requestBody;
        private String authorization;

        private StubRestTemplate(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType,
                Object... uriVariables) {
            this.url = url;
            HttpEntity<?> entity = (HttpEntity<?>) request;
            this.authorization = entity.getHeaders().getFirst("Authorization");
            this.requestBody = String.valueOf(entity.getBody());
            return new ResponseEntity<>((T) responseBody, HttpStatus.OK);
        }
    }
}
