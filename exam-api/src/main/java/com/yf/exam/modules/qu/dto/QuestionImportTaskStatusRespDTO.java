package com.yf.exam.modules.qu.dto;

import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(value = "AI导入任务状态")
public class QuestionImportTaskStatusRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("任务ID")
    private String taskId;

    @ApiModelProperty("状态")
    private String status;

    @ApiModelProperty("状态描述")
    private String statusLabel;

    @ApiModelProperty("进度 0-100")
    private Integer progress;

    @ApiModelProperty("当前阶段说明")
    private String message;

    @ApiModelProperty("处理模式：FAST/SMART/DEEP")
    private String importMode;

    @ApiModelProperty("总批次数")
    private Integer totalBatches;

    @ApiModelProperty("已完成批次数")
    private Integer completedBatches;

    @ApiModelProperty("失败批次数")
    private Integer failedBatchCount;

    @ApiModelProperty("进入深度清洗的批次数")
    private Integer deepCleanBatchCount;

    @ApiModelProperty("文件名")
    private String fileName;

    @ApiModelProperty("清洗前文本")
    private String rawText;

    @ApiModelProperty("清洗后文本")
    private String normalizedText;

    @ApiModelProperty("解析出的试题")
    private List<QuDetailDTO> questions;

    @ApiModelProperty("解析题数")
    private Integer count;

    @ApiModelProperty("失败原因")
    private String errorMessage;
}
