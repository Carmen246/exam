package com.yf.exam.modules.paper.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(value = "试卷Word导出请求", description = "试卷Word导出请求")
public class PaperWordExportReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "试卷ID", required = true)
    private String paperId;

    @ApiModelProperty(value = "是否导出参考答案")
    private Boolean includeAnswer = true;

    @ApiModelProperty(value = "是否导出答案解析")
    private Boolean includeAnalysis = true;
}
