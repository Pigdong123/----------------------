package com.campus.convert.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("format_template")
public class FormatTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceExt;

    private String targetExt;

    private String engine;

    private String paramsSchema;

    private Integer enabled;

    private LocalDateTime createdAt;
}
