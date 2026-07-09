package com.yf.exam.modules.sys.config.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.qu.config.AiUserContext;
import com.yf.exam.modules.sys.config.dto.SysAiConfigDTO;
import com.yf.exam.modules.sys.config.entity.SysAiConfig;
import com.yf.exam.modules.sys.config.mapper.SysAiConfigMapper;
import com.yf.exam.modules.sys.config.service.SysAiConfigService;
import com.yf.exam.modules.user.UserUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

@Service
@Slf4j
public class SysAiConfigServiceImpl extends ServiceImpl<SysAiConfigMapper, SysAiConfig> implements SysAiConfigService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public SysAiConfig getEntity() {
        return findSavedEntity(resolveUserId());
    }

    @Override
    public SysAiConfig getEntityForUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new ServiceException("用户 ID 不能为空");
        }
        return findSavedEntity(userId);
    }

    @Override
    public SysAiConfigDTO findDetail() {
        String userId = resolveUserId();
        SysAiConfig entity = findSavedEntity(userId);
        if (entity == null) {
            return buildEmptyDto(userId);
        }
        return toDto(entity);
    }

    @Override
    public void saveConfig(SysAiConfigDTO reqDTO) {
        if (reqDTO == null) {
            throw new ServiceException("AI 配置不能为空");
        }

        String userId = resolveUserId();
        SysAiConfig entity = findSavedEntity(userId);
        boolean isNew = entity == null;
        if (isNew) {
            entity = buildDefaultEntity(userId);
        }

        entity.setEnabled(true);
        entity.setProvider(trimToNull(reqDTO.getProvider()));
        entity.setBaseUrl(trimToNull(reqDTO.getBaseUrl()));
        entity.setChatId(trimToNull(reqDTO.getChatId()));
        entity.setModelName(trimToNull(reqDTO.getModelName()));
        entity.setTimeoutSeconds(reqDTO.getTimeoutSeconds());
        entity.setRagflowBaseUrl(trimToNull(reqDTO.getRagflowBaseUrl()));
        entity.setRagflowDatasetId(trimToNull(reqDTO.getRagflowDatasetId()));
        entity.setRagflowDatasetName(trimToNull(reqDTO.getRagflowDatasetName()));
        entity.setRagflowAutoUpload(reqDTO.getRagflowAutoUpload());
        entity.setRagflowUploadFailFast(reqDTO.getRagflowUploadFailFast());

        applySecretUpdate(entity::setApiKey, entity.getApiKey(), reqDTO.getApiKey());
        applySecretUpdate(entity::setRagflowApiKey, entity.getRagflowApiKey(), reqDTO.getRagflowApiKey());

        validateConfig(entity);

        Date now = new Date();
        entity.setUpdateTime(now);
        if (isNew) {
            entity.setCreateTime(now);
        }

        try {
            if (isNew) {
                save(entity);
            } else {
                updateById(entity);
            }
        } catch (DataAccessException e) {
            log.error("保存 sys_ai_config 失败，userId={}：{}", userId, e.getMessage(), e);
            throw new ServiceException("AI 配置保存失败，请确认已执行数据库脚本：docs/安装资源/sys_ai_config.sql");
        }
        eventPublisher.publishEvent(new SysAiConfigChangedEvent(this, userId));
    }

    private SysAiConfig findSavedEntity(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        try {
            return getById(userId);
        } catch (DataAccessException e) {
            log.warn("读取 sys_ai_config 失败，userId={}：{}", userId, e.getMessage());
            return null;
        }
    }

    private SysAiConfig buildDefaultEntity(String userId) {
        SysAiConfig created = new SysAiConfig();
        created.setId(userId);
        created.setEnabled(true);
        return created;
    }

    private SysAiConfigDTO buildEmptyDto(String userId) {
        SysAiConfigDTO dto = new SysAiConfigDTO();
        dto.setId(userId);
        dto.setEnabled(true);
        dto.setProvider("openai");
        dto.setTimeoutSeconds(60);
        dto.setApiKeyConfigured(false);
        dto.setRagflowApiKeyConfigured(false);
        dto.setRagflowAutoUpload(false);
        dto.setRagflowUploadFailFast(false);
        return dto;
    }

    private String resolveUserId() {
        String contextUserId = AiUserContext.getUserId();
        if (StringUtils.isNotBlank(contextUserId)) {
            return contextUserId;
        }
        return UserUtils.getUserId();
    }

    private SysAiConfigDTO toDto(SysAiConfig entity) {
        SysAiConfigDTO dto = new SysAiConfigDTO();
        dto.setId(entity.getId());
        dto.setEnabled(true);
        dto.setProvider(StringUtils.defaultIfBlank(entity.getProvider(), "openai"));
        dto.setBaseUrl(entity.getBaseUrl());
        dto.setApiKeyConfigured(StringUtils.isNotBlank(entity.getApiKey()));
        dto.setApiKey(dto.getApiKeyConfigured() ? SysAiConfigDTO.MASKED_SECRET : "");
        dto.setChatId(entity.getChatId());
        dto.setModelName(entity.getModelName());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds() == null ? 60 : entity.getTimeoutSeconds());
        dto.setRagflowBaseUrl(entity.getRagflowBaseUrl());
        dto.setRagflowApiKeyConfigured(StringUtils.isNotBlank(entity.getRagflowApiKey()));
        dto.setRagflowApiKey(dto.getRagflowApiKeyConfigured() ? SysAiConfigDTO.MASKED_SECRET : "");
        dto.setRagflowDatasetId(entity.getRagflowDatasetId());
        dto.setRagflowDatasetName(entity.getRagflowDatasetName());
        dto.setRagflowAutoUpload(Boolean.TRUE.equals(entity.getRagflowAutoUpload()));
        dto.setRagflowUploadFailFast(Boolean.TRUE.equals(entity.getRagflowUploadFailFast()));
        return dto;
    }

    private void validateConfig(SysAiConfig entity) {
        if (StringUtils.isBlank(entity.getProvider())) {
            throw new ServiceException("提供方不能为空");
        }
        if (StringUtils.isBlank(entity.getBaseUrl())) {
            throw new ServiceException("API 地址不能为空");
        }
        if (StringUtils.isBlank(entity.getApiKey())) {
            throw new ServiceException("API Key 不能为空");
        }
        if ("ragflow".equalsIgnoreCase(entity.getProvider()) && StringUtils.isBlank(entity.getChatId())) {
            throw new ServiceException("RAGFlow 模式下，聊天助手 ID 不能为空");
        }
    }

    private void applySecretUpdate(java.util.function.Consumer<String> setter, String currentValue, String incomingValue) {
        if (StringUtils.isBlank(incomingValue) || SysAiConfigDTO.MASKED_SECRET.equals(incomingValue)) {
            setter.accept(currentValue);
            return;
        }
        setter.accept(incomingValue.trim());
    }

    private String trimToNull(String value) {
        return StringUtils.trimToNull(value);
    }
}
