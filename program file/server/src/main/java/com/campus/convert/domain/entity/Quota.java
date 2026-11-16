package com.campus.convert.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("quota")
public class Quota implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer dailyLimit;

    private Integer usedCount;

    private Long storageLimit;

    private Long usedStorage;

    private LocalDate resetAt;

    private LocalDateTime createdAt;
}
