package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "格式能力目录")
public class FormatCatalogResponse {

    @Schema(description = "能力总数")
    private int total;

    @Schema(description = "扁平能力列表")
    private List<FormatOption> formats;

    @Schema(description = "按类别分组的能力列表")
    private Map<String, List<FormatOption>> grouped;
}
