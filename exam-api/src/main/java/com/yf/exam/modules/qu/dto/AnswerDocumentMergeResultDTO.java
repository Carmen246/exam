package com.yf.exam.modules.qu.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@ApiModel(value = "答案文档合并结果")
public class AnswerDocumentMergeResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("合并后的试卷文本")
    private String mergedText;

    @ApiModelProperty("合并提示/警告")
    private List<String> warnings = new ArrayList<>();
}
