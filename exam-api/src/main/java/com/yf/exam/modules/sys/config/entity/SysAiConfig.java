package com.yf.exam.modules.sys.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sys_ai_config")
public class SysAiConfig extends Model<SysAiConfig> {

    private static final long serialVersionUID = 1L;

    /** 主键使用用户 ID，每位管理员一条 AI 配置 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 是否启用数据库中的 AI 配置（覆盖 application.yml） */
    @TableField("enabled")
    private Boolean enabled;

    @TableField("provider")
    private String provider;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key")
    private String apiKey;

    @TableField("chat_id")
    private String chatId;

    @TableField("model_name")
    private String modelName;

    @TableField("timeout_seconds")
    private Integer timeoutSeconds;

    @TableField("ragflow_base_url")
    private String ragflowBaseUrl;

    @TableField("ragflow_api_key")
    private String ragflowApiKey;

    @TableField("ragflow_dataset_id")
    private String ragflowDatasetId;

    @TableField("ragflow_dataset_name")
    private String ragflowDatasetName;

    @TableField("ragflow_auto_upload")
    private Boolean ragflowAutoUpload;

    @TableField("ragflow_upload_fail_fast")
    private Boolean ragflowUploadFailFast;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
