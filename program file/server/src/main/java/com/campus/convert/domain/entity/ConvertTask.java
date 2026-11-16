package com.campus.convert.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("convert_task")
public class ConvertTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sourceFileId;

    private String targetFormat;

    private String params;

    private String status;

    private Integer priority;

    private String errorMsg;

    private Integer retryCount;

    /** 转换产物文件ID（成功后由 FileService 登记生成） */
    private Long resultFileId;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 引擎实际写出路径（运行期使用，不持久化） */
    @TableField(exist = false)
    private String outputPath;
}
