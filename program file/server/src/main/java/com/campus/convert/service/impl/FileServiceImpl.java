package com.campus.convert.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.convert.ai.AiService;
import com.campus.convert.common.BaseContext;
import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.dto.FileUploadResponse;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.domain.enums.FileStatusEnum;
import com.campus.convert.mapper.ConvertTaskMapper;
import com.campus.convert.mapper.FileRecordMapper;
import com.campus.convert.service.FileService;
import com.campus.convert.service.QuotaService;
import com.campus.convert.storage.StorageService;
import com.campus.convert.storage.impl.LocalStorageServiceImpl;
import com.campus.convert.util.FileHashUtil;
import com.campus.convert.util.FileTypeDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    private final StorageService storageService;
    private final FileRecordMapper fileRecordMapper;
    private final ConvertTaskMapper convertTaskMapper;
    private final QuotaService quotaService;
    private final AiService aiService;

    @Value("${campus.file.allowed-extensions:}")
    private String allowedExtensions;

    @Value("${campus.file.expire-days:7}")
    private int expireDays;

    @Value("${campus.quota.max-file-size:200MB}")
    private String maxFileSizeText;

    @Override
    public FileUploadResponse upload(MultipartFile file) {
        Long userId = BaseContext.getUserId();
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "上传文件为空");
        }
        long size = file.getSize();
        if (size > maxFileSizeBytes()) {
            throw new BizException(ResultCode.FILE_TOO_LARGE,
                    "文件超过大小限制（" + maxFileSizeText + "）");
        }
        String ext = resolveExt(file);
        quotaService.assertStorageAvailable(userId, size);

        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "upload." + ext;
        String storageName = LocalStorageServiceImpl.generateStorageName(originalName);
        String hash = sha256(file);

        String storagePath = null;
        FileRecord record = null;
        try {
            storagePath = storageService.store(file.getInputStream(), storageName, size);

            record = new FileRecord();
            record.setUserId(userId);
            record.setOriginalName(originalName);
            record.setStoragePath(storagePath);
            record.setExt(ext);
            record.setMime(file.getContentType());
            record.setSize(size);
            record.setHash(hash);
            record.setStatus(FileStatusEnum.UPLOADED.name());
            record.setExpireAt(LocalDateTime.now().plusDays(expireDays));
            record.setCreatedAt(LocalDateTime.now());
            fileRecordMapper.insert(record);

            quotaService.addStorageUsage(userId, size);
        } catch (IOException e) {
            rollbackUpload(storagePath, record);
            throw new BizException(ResultCode.FILE_CORRUPTED, "文件读取失败");
        } catch (RuntimeException e) {
            // 修复点：落库/计数失败时清理已写入的文件与记录，避免孤儿文件
            rollbackUpload(storagePath, record);
            throw e;
        }

        FileUploadResponse response = new FileUploadResponse();
        response.setFileId(record.getId());
        response.setOriginalName(record.getOriginalName());
        response.setExt(record.getExt());
        response.setSize(record.getSize());
        response.setRecommendedFormats(aiService.recommendFormats(ext));
        response.setRemainingQuota(quotaService.remaining(userId));
        return response;
    }

    @Override
    public FileRecord getOwned(Long fileId) {
        FileRecord record = fileRecordMapper.selectById(fileId);
        if (record == null || FileStatusEnum.DELETED.name().equals(record.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
        }
        if (!record.getUserId().equals(BaseContext.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该文件");
        }
        return record;
    }

    @Override
    public PageResponse<FileRecord> pageMine(long pageNum, long pageSize) {
        Long userId = BaseContext.getUserId();
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), 100);
        IPage<FileRecord> page = fileRecordMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<FileRecord>lambdaQuery()
                        .eq(FileRecord::getUserId, userId)
                        .ne(FileRecord::getStatus, FileStatusEnum.DELETED.name())
                        .orderByDesc(FileRecord::getCreatedAt));
        return PageResponse.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    public void deleteOwn(Long fileId) {
        FileRecord record = getOwned(fileId);
        // 修复点：源文件正在被转换时删除会导致任务必然失败，先拦截
        if (convertTaskMapper.countActiveBySourceFile(fileId) > 0) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID,
                    "该文件存在进行中的转换任务，暂不能删除");
        }
        storageService.delete(record.getStoragePath());
        record.setStatus(FileStatusEnum.DELETED.name());
        fileRecordMapper.updateById(record);
        quotaService.addStorageUsage(record.getUserId(),
                -(record.getSize() == null ? 0L : record.getSize()));
    }

    @Override
    public FileRecord registerResult(ConvertTask task, String outputPath) {
        long size = resolveSize(outputPath);
        // 修复点：产物同样占用存储配额，原实现未计入导致配额失真
        quotaService.assertStorageAvailable(task.getUserId(), size);

        FileRecord record = new FileRecord();
        record.setUserId(task.getUserId());
        record.setOriginalName("result_" + task.getId() + "." + task.getTargetFormat());
        record.setStoragePath(outputPath);
        record.setExt(task.getTargetFormat());
        record.setMime(guessMime(task.getTargetFormat()));
        record.setSize(size);
        record.setHash(hashFile(outputPath));
        record.setStatus(FileStatusEnum.CONVERTED.name());
        record.setExpireAt(LocalDateTime.now().plusDays(expireDays));
        record.setCreatedAt(LocalDateTime.now());
        fileRecordMapper.insert(record);
        quotaService.addStorageUsage(task.getUserId(), size);
        return record;
    }

    @Override
    public int cleanupExpired() {
        List<FileRecord> expired = fileRecordMapper.selectExpired(LocalDateTime.now());
        for (FileRecord record : expired) {
            storageService.delete(record.getStoragePath());
            record.setStatus(FileStatusEnum.EXPIRED.name());
            fileRecordMapper.updateById(record);
            quotaService.addStorageUsage(record.getUserId(),
                    -(record.getSize() == null ? 0L : record.getSize()));
        }
        return expired.size();
    }

    private String resolveExt(MultipartFile file) {
        String detected = null;
        try {
            detected = FileTypeDetector.detectExtension(file);
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_CORRUPTED, "无法读取文件头");
        }
        String fromName = null;
        String originalName = file.getOriginalFilename();
        if (StringUtils.hasText(originalName) && originalName.contains(".")) {
            fromName = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        String ext = detected != null ? detected : fromName;
        if (ext == null || !allowedExtSet().contains(ext)) {
            throw new BizException(ResultCode.FORMAT_NOT_SUPPORTED, "不支持的源格式: " + ext);
        }
        return ext;
    }

    private Set<String> allowedExtSet() {
        return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private String sha256(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return FileHashUtil.sha256(in);
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_CORRUPTED, "计算文件哈希失败");
        }
    }

    private long resolveSize(String path) {
        if (path == null) {
            return 0L;
        }
        try {
            return Files.size(Paths.get(path));
        } catch (IOException e) {
            return 0L;
        }
    }

    private String guessMime(String ext) {
        return switch (ext == null ? "" : ext.toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "mp3" -> "audio/mpeg";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private long maxFileSizeBytes() {
        try {
            return DataSize.parse(maxFileSizeText).toBytes();
        } catch (Exception e) {
            return 200L * 1024 * 1024;
        }
    }

    private String hashFile(String path) {
        if (path == null) {
            return null;
        }
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            return FileHashUtil.sha256(in);
        } catch (IOException e) {
            log.warn("计算产物哈希失败: {}", path, e);
            return null;
        }
    }

    private void rollbackUpload(String storagePath, FileRecord record) {
        if (record != null && record.getId() != null) {
            try {
                fileRecordMapper.deleteById(record.getId());
            } catch (Exception e) {
                log.warn("回滚文件记录失败: {}", record.getId(), e);
            }
        }
        if (storagePath != null) {
            try {
                storageService.delete(storagePath);
            } catch (Exception e) {
                log.warn("回滚已存储文件失败: {}", storagePath, e);
            }
        }
    }
}
