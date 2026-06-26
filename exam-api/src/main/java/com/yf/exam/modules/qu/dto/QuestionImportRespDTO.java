package com.yf.exam.modules.qu.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(value = "AI试题确认导入响应")
public class QuestionImportRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("成功导入数量")
    private Integer count;
}