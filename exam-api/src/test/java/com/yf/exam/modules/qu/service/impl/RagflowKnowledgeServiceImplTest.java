package com.yf.exam.modules.qu.service.impl;

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

        RagflowKnowledgeServiceImpl service = new RagflowKnowledgeServiceImpl();
        injectProperties(service, properties);

        assertTrue(service.isAutoUploadRequested());
        assertTrue(service.isReadyForUpload());
        assertEquals("dataset-1", service.getDatasetId());
    }

    private void injectProperties(RagflowKnowledgeServiceImpl service, QuestionAiProperties properties)
            throws Exception {
        Field field = RagflowKnowledgeServiceImpl.class.getDeclaredField("properties");
        field.setAccessible(true);
        field.set(service, properties);
    }
}
