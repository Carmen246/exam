package com.yf.exam.modules.qu.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(value = "AI试题解析请求")
public class QuestionParseReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("文档解析后的纯文本")
    private String text;

    @ApiModelProperty("题库ID列表")
    private List<String> repoIds;

    @ApiModelProperty("默认难度：1普通，2较难")
    private Integer level = 1;
}