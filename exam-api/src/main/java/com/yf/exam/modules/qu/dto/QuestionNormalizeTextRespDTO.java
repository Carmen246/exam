package com.yf.exam.modules.qu.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionNormalizeTextRespDTO implements Serializable {

    private String normalizedText;

    private String rawText;
}