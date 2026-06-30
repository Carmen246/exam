package com.yf.exam.modules.qu.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(value = "导入批次状态")
public class ImportBatchStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("批次序号（从1开始）")
    private Integer batchNo;

    @ApiModelProperty("处理阶段：PARSE / DEEP_NORMALIZE / RETRY")
    private String phase;

    @ApiModelProperty("状态：PENDING / RUNNING / SUCCESS / FAILED")
    private String status;

    @ApiModelProperty("成功解析出的题数")
    private Integer questionCount;

    @ApiModelProperty("失败原因")
    private String errorMessage;

    @ApiModelProperty("该批文本预览，便于定位原文")
    private String previewText;
}
