package com.campus.convert.storage.impl;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件系统存储实现（默认）
 */
@Service
@ConditionalOnProperty(name = "campus.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageServiceImpl.class);

    private final Path basePath;

    public LocalStorageServiceImpl(@Value("${campus.storage.local-path}") String localPath) {
        this.basePath = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建存储目录: " + this.basePath, e);
        }
    }

    @Override
    public String store(InputStream inputStream, String fileName, long size) {
        Path target = basePath.resolve(fileName).normalize();
        // 防止路径穿越
        if (!target.startsWith(basePath)) {
            throw new BizException(ResultCode.FORBIDDEN, "非法存储路径");
        }
        try (InputStream in = inputStream) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("文件存储失败: {}", fileName, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件存储失败");
        }
        return target.toString();
    }

    @Override
    public InputStream load(String storagePath) {
        Path target = Paths.get(storagePath).normalize();
        if (!target.startsWith(basePath)) {
            throw new BizException(ResultCode.FORBIDDEN, "非法访问路径");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
        }
    }

    @Override
    public void delete(String storagePath) {
        Path target = Paths.get(storagePath).normalize();
        if (!target.startsWith(basePath)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("文件删除失败: {}", storagePath, e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return false;
        }
        Path target = Paths.get(storagePath).toAbsolutePath().normalize();
        // 修复点：原实现未做边界校验，传任意路径均可探测本机文件是否存在
        if (!target.startsWith(basePath)) {
            return false;
        }
        return Files.isRegularFile(target);
    }

    @Override
    public long usedStorageBytes() {
        // TODO: 大目录下可改为定时采样并缓存；此处为框架实现的轻量统计
        try (java.util.stream.Stream<Path> stream = Files.walk(basePath)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.warn("统计存储占用失败", e);
            return 0L;
        }
    }

    @Override
    public long availableStorageBytes() {
        try {
            return Files.getFileStore(basePath).getUsableSpace();
        } catch (IOException e) {
            log.warn("读取磁盘剩余空间失败", e);
            return 0L;
        }
    }

    @Override
    public String prepareOutputPath(String originalName, String targetExt) {
        String fileName = generateStorageName("output." + targetExt);
        Path target = basePath.resolve("output").resolve(fileName).normalize();
        if (!target.startsWith(basePath)) {
            throw new BizException(ResultCode.FORBIDDEN, "非法存储路径");
        }
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "无法创建产物目录");
        }
        return target.toString();
    }

    /**
     * 生成带随机前缀的存储文件名，避免冲突与信息泄露
     */
    public static String generateStorageName(String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        return UUID.randomUUID().toString().replace("-", "") + ext;
    }
}
