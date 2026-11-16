package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "分页响应")
public class PageResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页码")
    private long pageNum;

    @Schema(description = "每页条数")
    private long pageSize;

    @Schema(description = "数据列表")
    private List<T> records;

    public static <T> PageResponse<T> of(long total, long pageNum, long pageSize, List<T> records) {
        PageResponse<T> response = new PageResponse<>();
        response.setTotal(total);
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        response.setRecords(records);
        return response;
    }
}
