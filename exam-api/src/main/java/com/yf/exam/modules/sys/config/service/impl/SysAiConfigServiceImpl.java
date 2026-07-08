package com.yf.exam.modules.sys.config.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.exam.core.exception.ServiceException;
import com.yf.exam.modules.sys.config.dto.SysAiConfigDTO;
import com.yf.exam.modules.sys.config.entity.SysAiConfig;
import com.yf.exam.modules.sys.config.mapper.SysAiConfigMapper;
import com.yf.exam.modules.sys.config.service.SysAiConfigService;
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
        return getOrCreateEntity();
    }

    @Override
    public SysAiConfigDTO findDetail() {
        SysAiConfig entity = getOrCreateEntity();
        return toDto(entity);
    }

    @Override
    public void saveConfig(SysAiConfigDTO reqDTO) {
        if (reqDTO == null) {
            throw new ServiceException("AI 配置不能为空");
        }

        SysAiConfig entity = getOrCreateEntity();
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
        entity.setUpdateTime(new Date());
        try {
            saveOrUpdate(entity);
        } catch (DataAccessException e) {
            log.error("保存 sys_ai_config 失败：{}", e.getMessage(), e);
            throw new ServiceException("AI 配置表不存在，请先执行数据库脚本：docs/安装资源/sys_ai_config.sql");
        }
        eventPublisher.publishEvent(new SysAiConfigChangedEvent(this));
    }

    SysAiConfig getOrCreateEntity() {
        try {
            SysAiConfig entity = getById(SysAiConfig.DEFAULT_ID);
            if (entity != null) {
                if (!Boolean.TRUE.equals(entity.getEnabled())) {
                    entity.setEnabled(true);
                    entity.setUpdateTime(new Date());
                    updateById(entity);
                }
                return entity;
            }

            SysAiConfig created = buildDefaultEntity();
            save(created);
            return created;
        } catch (DataAccessException e) {
            log.warn("读取 sys_ai_config 失败，可能尚未执行数据库脚本 docs/安装资源/sys_ai_config.sql：{}", e.getMessage());
            return buildDefaultEntity();
        }
    }

    private SysAiConfig buildDefaultEntity() {
        SysAiConfig created = new SysAiConfig();
        created.setId(SysAiConfig.DEFAULT_ID);
        created.setEnabled(true);
        created.setCreateTime(new Date());
        created.setUpdateTime(new Date());
        return created;
    }

    private SysAiConfigDTO toDto(SysAiConfig entity) {
        SysAiConfigDTO dto = new SysAiConfigDTO();
        dto.setId(entity.getId());
        dto.setEnabled(true);
        dto.setProvider(entity.getProvider());
        dto.setBaseUrl(entity.getBaseUrl());
        dto.setApiKeyConfigured(StringUtils.isNotBlank(entity.getApiKey()));
        dto.setApiKey(dto.getApiKeyConfigured() ? SysAiConfigDTO.MASKED_SECRET : "");
        dto.setChatId(entity.getChatId());
        dto.setModelName(entity.getModelName());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds());
        dto.setRagflowBaseUrl(entity.getRagflowBaseUrl());
        dto.setRagflowApiKeyConfigured(StringUtils.isNotBlank(entity.getRagflowApiKey()));
        dto.setRagflowApiKey(dto.getRagflowApiKeyConfigured() ? SysAiConfigDTO.MASKED_SECRET : "");
        dto.setRagflowDatasetId(entity.getRagflowDatasetId());
        dto.setRagflowDatasetName(entity.getRagflowDatasetName());
        dto.setRagflowAutoUpload(entity.getRagflowAutoUpload());
        dto.setRagflowUploadFailFast(entity.getRagflowUploadFailFast());
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
