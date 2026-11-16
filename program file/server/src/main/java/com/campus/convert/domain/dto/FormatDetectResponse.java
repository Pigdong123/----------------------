package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "上传文件格式识别结果")
public class FormatDetectResponse {

    @Schema(description = "原始文件名")
    private String filename;

    @Schema(description = "识别到的源格式（魔数优先，扩展名兜底）", example = "heic")
    private String detectedExt;

    @Schema(description = "可用的目标格式")
    private List<String> targetExts;

    @Schema(description = "可用的转换能力明细")
    private List<FormatOption> options;
}
