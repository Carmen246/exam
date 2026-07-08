package com.yf.exam.modules.qu.service.impl;

import com.yf.exam.modules.qu.config.QuestionAiConfigProvider;
import com.yf.exam.modules.qu.config.QuestionAiProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagflowKnowledgeServiceImplTest {

    @Test
    void autoUploadUsesDedicatedRagflowConfigWhenParserProviderIsOpenAi() throws Exception {
        QuestionAiProperties properties = new QuestionAiProperties();
        properties.setProvider("openai");
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setApiKey("deepseek-key");
        properties.setRagflowAutoUpload(true);
        properties.setRagflowBaseUrl("http://localhost:9380");
        properties.setRagflowApiKey("ragflow-key");
        properties.setRagflowDatasetId("dataset-1");

        QuestionAiConfigProvider provider = new QuestionAiConfigProvider() {
            @Override
            public QuestionAiProperties getEffective() {
                return properties;
            }
        };

        RagflowKnowledgeServiceImpl service = new RagflowKnowledgeServiceImpl();
        injectProvider(service, provider);

        assertTrue(service.isAutoUploadRequested());
        assertTrue(service.isReadyForUpload());
        assertEquals("dataset-1", service.getDatasetId());
    }

    private void injectProvider(RagflowKnowledgeServiceImpl service, QuestionAiConfigProvider provider)
            throws Exception {
        Field field = RagflowKnowledgeServiceImpl.class.getDeclaredField("questionAiConfigProvider");
        field.setAccessible(true);
        field.set(service, provider);
    }
}
