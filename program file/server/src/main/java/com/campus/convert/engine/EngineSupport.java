package com.campus.convert.engine;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 引擎通用辅助：产物目录创建、产物有效性校验、文件名处理。
 */
public final class EngineSupport {

    private EngineSupport() {
    }

    public static void ensureParent(String targetPath) {
        try {
            Path parent = Paths.get(targetPath).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "无法创建产物目录");
        }
    }

    public static void ensureOutput(String targetPath, String engineName) {
        Path path = Paths.get(targetPath);
        try {
            if (!Files.isRegularFile(path) || Files.size(path) <= 0) {
                throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                        engineName + "未生成有效产物（文件缺失或为空）");
            }
        } catch (IOException e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                    engineName + "产物校验失败: " + e.getMessage());
        }
    }

    public static String baseName(String path) {
        String name = Paths.get(path).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
