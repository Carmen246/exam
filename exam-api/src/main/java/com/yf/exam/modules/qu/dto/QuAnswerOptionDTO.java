package com.yf.exam.modules.qu.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(value = "程序填空候选项", description = "程序填空题单个空位的候选项")
public class QuAnswerOptionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "选项字母 A-D")
    private String letter;

    @ApiModelProperty(value = "选项内容")
    private String content;

    @ApiModelProperty(value = "是否为该空正确答案")
    private Boolean isRight;

    @ApiModelProperty(value = "选项解析")
    private String analysis;
}
