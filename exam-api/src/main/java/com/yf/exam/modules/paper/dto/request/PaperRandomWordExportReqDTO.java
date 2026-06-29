package com.yf.exam.modules.paper.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@ApiModel(value = "题库随机试卷Word导出请求", description = "题库随机试卷Word导出请求")
public class PaperRandomWordExportReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "试卷标题")
    private String title;

    @ApiModelProperty(value = "题库ID列表", required = true)
    private List<String> repoIds;

    @ApiModelProperty(value = "单选题数量")
    private Integer radioCount = 0;

    @ApiModelProperty(value = "单选题分值")
    private Integer radioScore = 0;

    @ApiModelProperty(value = "多选题数量")
    private Integer multiCount = 0;

    @ApiModelProperty(value = "多选题分值")
    private Integer multiScore = 0;

    @ApiModelProperty(value = "判断题数量")
    private Integer judgeCount = 0;

    @ApiModelProperty(value = "判断题分值")
    private Integer judgeScore = 0;

    @ApiModelProperty(value = "考试时长，单位分钟")
    private Integer totalTime;

    @ApiModelProperty(value = "是否导出参考答案")
    private Boolean includeAnswer = true;

    @ApiModelProperty(value = "是否导出答案解析")
    private Boolean includeAnalysis = true;
}
