package com.campus.convert.controller;

import com.campus.convert.common.Result;
import com.campus.convert.domain.dto.FormatCatalogResponse;
import com.campus.convert.domain.dto.FormatDetectResponse;
import com.campus.convert.domain.dto.FormatOption;
import com.campus.convert.service.FormatCatalogService;
import com.campus.convert.util.FileTypeDetector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 多格式转换能力接口：
 * - 能力目录（按类别分组）
 * - 按源格式查询目标格式
 * - 上传文件格式识别 + 推荐目标格式
 */
@Tag(name = "格式能力")
@RestController
@RequestMapping("/formats")
@RequiredArgsConstructor
public class FormatController {

    private final FormatCatalogService formatCatalogService;

    @Operation(summary = "获取全部转换能力（按类别分组）")
    @GetMapping
    public Result<FormatCatalogResponse> catalog() {
        return Result.ok(formatCatalogService.catalog());
    }

    @Operation(summary = "按源格式查询可用的目标格式")
    @GetMapping("/{sourceExt}")
    public Result<List<FormatOption>> bySource(@PathVariable String sourceExt) {
        return Result.ok(formatCatalogService.listBySource(sourceExt));
    }

    @Operation(summary = "识别上传文件格式并返回可用目标格式")
    @PostMapping("/detect")
    public Result<FormatDetectResponse> detect(@RequestParam("file") MultipartFile file)
            throws IOException {
        String detected = FileTypeDetector.detectExtension(file);
        String original = file.getOriginalFilename();
        String fromName = null;
        if (original != null && original.contains(".")) {
            fromName = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        String ext = detected != null ? detected : fromName;

        FormatDetectResponse response = new FormatDetectResponse();
        response.setFilename(original);
        response.setDetectedExt(ext);
        if (ext == null) {
            response.setOptions(List.of());
            response.setTargetExts(List.of());
            return Result.ok(response);
        }

        List<FormatOption> options = formatCatalogService.listBySource(ext);
        response.setOptions(options);
        response.setTargetExts(options.stream()
                .map(FormatOption::getTargetExt)
                .distinct()
                .toList());
        return Result.ok(response);
    }
}
