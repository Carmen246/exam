package com.yf.exam.modules.qu.config;

import com.yf.exam.modules.sys.config.entity.SysAiConfig;
import com.yf.exam.modules.sys.config.service.SysAiConfigService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QuestionAiConfigProvider {

    @Autowired
    private QuestionAiProperties ymlProperties;

    @Autowired
    private SysAiConfigService sysAiConfigService;

    public QuestionAiProperties getEffective() {
        return resolveEffective(null);
    }

    /**
     * 合并 yml、数据库与页面草稿配置（草稿中的密钥留空时使用已保存值）。
     */
    public QuestionAiProperties resolveEffective(com.yf.exam.modules.sys.config.dto.SysAiConfigDTO draft) {
        QuestionAiProperties effective = copy(ymlProperties);
        SysAiConfig dbConfig = sysAiConfigService.getEntity();
        if (draft == null) {
            mergeDbConfig(effective, dbConfig);
        } else {
            mergeDraftConfig(effective, dbConfig, draft);
        }
        return effective;
    }

    private QuestionAiProperties copy(QuestionAiProperties source) {
        QuestionAiProperties target = new QuestionAiProperties();
        target.setProvider(source.getProvider());
        target.setBaseUrl(source.getBaseUrl());
        target.setApiKey(source.getApiKey());
        target.setChatId(source.getChatId());
        target.setRagflowBaseUrl(source.getRagflowBaseUrl());
        target.setRagflowApiKey(source.getRagflowApiKey());
        target.setRagflowDatasetId(source.getRagflowDatasetId());
        target.setRagflowDatasetName(source.getRagflowDatasetName());
        target.setRagflowDatasetPageSize(source.getRagflowDatasetPageSize());
        target.setRagflowAutoUpload(source.getRagflowAutoUpload());
        target.setRagflowUploadFailFast(source.getRagflowUploadFailFast());
        target.setRagflowUploadTimeoutSeconds(source.getRagflowUploadTimeoutSeconds());
        target.setModelName(source.getModelName());
        target.setTimeoutSeconds(source.getTimeoutSeconds());
        target.setMaxTextLength(source.getMaxTextLength());
        target.setNormalizeBatchLength(source.getNormalizeBatchLength());
        target.setNormalizeBatchQuestionCount(source.getNormalizeBatchQuestionCount());
        target.setNormalizeConcurrency(source.getNormalizeConcurrency());
        target.setParseFallbackRatio(source.getParseFallbackRatio());
        target.setParseFallbackMinExpected(source.getParseFallbackMinExpected());
        target.setAiConcurrency(source.getAiConcurrency());
        return target;
    }

    private void mergeDbConfig(QuestionAiProperties target, SysAiConfig dbConfig) {
        if (StringUtils.isNotBlank(dbConfig.getProvider())) {
            target.setProvider(dbConfig.getProvider());
        }
        if (StringUtils.isNotBlank(dbConfig.getBaseUrl())) {
            target.setBaseUrl(dbConfig.getBaseUrl());
        }
        if (StringUtils.isNotBlank(dbConfig.getApiKey())) {
            target.setApiKey(dbConfig.getApiKey());
        }
        if (StringUtils.isNotBlank(dbConfig.getChatId())) {
            target.setChatId(dbConfig.getChatId());
        }
        if (StringUtils.isNotBlank(dbConfig.getModelName())) {
            target.setModelName(dbConfig.getModelName());
        }
        if (dbConfig.getTimeoutSeconds() != null) {
            target.setTimeoutSeconds(dbConfig.getTimeoutSeconds());
        }
        if (StringUtils.isNotBlank(dbConfig.getRagflowBaseUrl())) {
            target.setRagflowBaseUrl(dbConfig.getRagflowBaseUrl());
        }
        if (StringUtils.isNotBlank(dbConfig.getRagflowApiKey())) {
            target.setRagflowApiKey(dbConfig.getRagflowApiKey());
        }
        if (StringUtils.isNotBlank(dbConfig.getRagflowDatasetId())) {
            target.setRagflowDatasetId(dbConfig.getRagflowDatasetId());
        }
        if (StringUtils.isNotBlank(dbConfig.getRagflowDatasetName())) {
            target.setRagflowDatasetName(dbConfig.getRagflowDatasetName());
        }
        if (dbConfig.getRagflowAutoUpload() != null) {
            target.setRagflowAutoUpload(dbConfig.getRagflowAutoUpload());
        }
        if (dbConfig.getRagflowUploadFailFast() != null) {
            target.setRagflowUploadFailFast(dbConfig.getRagflowUploadFailFast());
        }
    }

    private void mergeDraftConfig(QuestionAiProperties target, SysAiConfig dbConfig,
            com.yf.exam.modules.sys.config.dto.SysAiConfigDTO draft) {
        mergeDbConfig(target, dbConfig);
        if (StringUtils.isNotBlank(draft.getProvider())) {
            target.setProvider(draft.getProvider());
        }
        if (StringUtils.isNotBlank(draft.getBaseUrl())) {
            target.setBaseUrl(draft.getBaseUrl());
        }
        if (StringUtils.isNotBlank(draft.getChatId())) {
            target.setChatId(draft.getChatId());
        }
        if (StringUtils.isNotBlank(draft.getModelName())) {
            target.setModelName(draft.getModelName());
        }
        if (draft.getTimeoutSeconds() != null) {
            target.setTimeoutSeconds(draft.getTimeoutSeconds());
        }
        target.setApiKey(resolveDraftSecret(draft.getApiKey(), dbConfig.getApiKey()));
    }

    private String resolveDraftSecret(String incoming, String saved) {
        if (StringUtils.isBlank(incoming)
                || com.yf.exam.modules.sys.config.dto.SysAiConfigDTO.MASKED_SECRET.equals(incoming)) {
            return saved;
        }
        return incoming.trim();
    }
}
