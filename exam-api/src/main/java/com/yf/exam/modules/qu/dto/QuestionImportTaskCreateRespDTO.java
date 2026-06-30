package com.yf.exam.modules.qu.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(value = "AI导入任务创建响应")
public class QuestionImportTaskCreateRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("任务ID")
    private String taskId;

    @ApiModelProperty("文件名")
    private String fileName;
}
