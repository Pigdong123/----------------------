package com.campus.convert.engine;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.domain.entity.FormatTemplate;
import com.campus.convert.mapper.FileRecordMapper;
import com.campus.convert.service.FormatTemplateService;
import com.campus.convert.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 引擎注册中心：按格式模板中的引擎类型路由到具体实现
 * <p>
 * 职责：
 * 1. 依据任务的源文件扩展名 + 目标格式解析格式模板（决定引擎与参数）
 * 2. 通过 {@link StorageService#prepareOutputPath} 分配产物路径
 * 3. 路由到匹配的 {@link ConversionEngine} 并回填产物路径
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EngineRegistry {

    private final List<ConversionEngine> engines;
    private final FileRecordMapper fileRecordMapper;
    private final FormatTemplateService formatTemplateService;
    private final StorageService storageService;

    /**
     * 执行转换（路由到匹配的引擎）
     *
     * @param task 转换任务
     * @return 更新后的任务（含 outputPath 产物路径）
     * @throws BizException 源文件缺失、格式模板未配置或无匹配引擎时抛出
     */
    public ConvertTask execute(ConvertTask task) {
        FileRecord source = fileRecordMapper.selectById(task.getSourceFileId());
        if (source == null) {
            throw new BizException(ResultCode.FILE_EXPIRED, "源文件不存在或已过期");
        }
        FormatTemplate template = formatTemplateService.resolve(source.getExt(), task.getTargetFormat());
        if (template == null) {
            throw new BizException(ResultCode.FORMAT_NOT_SUPPORTED,
                    "暂不支持 " + source.getExt() + " → " + task.getTargetFormat());
        }
        EngineType engineType = EngineType.fromCode(template.getEngine());
        String targetPath = storageService.prepareOutputPath(source.getOriginalName(), task.getTargetFormat());

        for (ConversionEngine engine : engines) {
            if (engine.getType() == engineType
                    && engine.supports(template.getSourceExt(), template.getTargetExt())) {
                try {
                    ConvertTask converted = engine.convert(task, source.getStoragePath(), targetPath);
                    converted.setOutputPath(targetPath);
                    return converted;
                } catch (RuntimeException e) {
                    // 引擎失败时清理可能已写出的半成品，避免孤儿文件与存储泄漏
                    storageService.delete(targetPath);
                    throw e;
                }
            }
        }
        throw new BizException(ResultCode.FORMAT_NOT_SUPPORTED, "未找到可用的转换引擎: " + engineType.getCode());
    }

    /**
     * 各引擎健康检查（运维端）
     */
    public boolean allEnginesHealthy() {
        // 可选引擎（CAJ/DjVu/Pages/OCR 等）依赖的外部工具缺失时不拖垮整体健康判定
        List<ConversionEngine> required = engines.stream()
                .filter(e -> !e.optional())
                .toList();
        return !required.isEmpty() && required.stream().allMatch(ConversionEngine::healthCheck);
    }

    /**
     * 引擎信息（运维端展示）
     */
    public List<String> engineInfo() {
        return engines.stream()
                .map(e -> e.getType().getCode()
                        + (e.optional() ? "(可选)" : "")
                        + ": " + (e.healthCheck() ? "正常" : "异常"))
                .toList();
    }
}
