package com.yf.exam.modules.sys.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yf.exam.modules.sys.config.dto.SysAiConfigDTO;
import com.yf.exam.modules.sys.config.entity.SysAiConfig;

public interface SysAiConfigService extends IService<SysAiConfig> {

    SysAiConfigDTO findDetail();

    SysAiConfig getEntity();

    SysAiConfig getEntityForUser(String userId);

    void saveConfig(SysAiConfigDTO reqDTO);
}
