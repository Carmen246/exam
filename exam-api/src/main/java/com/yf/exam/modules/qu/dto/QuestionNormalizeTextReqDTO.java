package com.yf.exam.modules.qu.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionNormalizeTextReqDTO implements Serializable {

    private String text;
}