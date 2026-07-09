package com.yf.exam.modules.sys.config.service.impl;

import org.springframework.context.ApplicationEvent;

public class SysAiConfigChangedEvent extends ApplicationEvent {

    private final String userId;

    public SysAiConfigChangedEvent(Object source, String userId) {
        super(source);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
