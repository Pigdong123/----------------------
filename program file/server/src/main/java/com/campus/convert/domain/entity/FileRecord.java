package com.campus.convert.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("file_record")
public class FileRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String originalName;

    private String storagePath;

    private String ext;

    private String mime;

    private Long size;

    private String hash;

    private String status;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;
}
