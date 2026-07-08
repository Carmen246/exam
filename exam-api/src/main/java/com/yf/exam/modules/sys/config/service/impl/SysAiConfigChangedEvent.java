package com.yf.exam.modules.sys.config.service.impl;

import org.springframework.context.ApplicationEvent;

public class SysAiConfigChangedEvent extends ApplicationEvent {

    public SysAiConfigChangedEvent(Object source) {
        super(source);
    }
}
