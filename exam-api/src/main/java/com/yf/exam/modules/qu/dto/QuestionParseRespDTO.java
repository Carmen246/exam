package com.yf.exam.modules.qu.dto;

import com.yf.exam.modules.qu.dto.ext.QuDetailDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(value = "AI试题解析响应")
public class QuestionParseRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("解析出的题目数量")
    private Integer count;

    @ApiModelProperty("解析出的题目列表")
    private List<QuDetailDTO> questions;

    @ApiModelProperty("大模型原始JSON")
    private String rawJson;
}