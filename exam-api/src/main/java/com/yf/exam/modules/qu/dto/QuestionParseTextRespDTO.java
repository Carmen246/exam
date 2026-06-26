package com.yf.exam.modules.qu.dto;

import lombok.Data;

@Data
public class QuestionParseTextRespDTO {

    private String fileName;

    private String fileType;

    private Integer charCount;

    private String previewText;

    private String rawText;
}