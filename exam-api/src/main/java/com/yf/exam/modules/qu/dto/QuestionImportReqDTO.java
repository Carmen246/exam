package com.yf.exam.modules.qu.dto;

import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(value = "AI试题确认导入请求")
public class QuestionImportReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("确认导入的试题列表")
    private List<QuDetailDTO> questions;

    @ApiModelProperty("AI 导入任务 ID（用于校验任务归属）")
    private String taskId;
}