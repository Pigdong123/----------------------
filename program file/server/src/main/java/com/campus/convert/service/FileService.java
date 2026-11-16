package com.campus.convert.service;

import com.campus.convert.domain.dto.FileUploadResponse;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.FileRecord;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务：上传校验、归属控制、产物登记、过期清理
 */
public interface FileService {

    FileUploadResponse upload(MultipartFile file);

    FileRecord getOwned(Long fileId);

    PageResponse<FileRecord> pageMine(long pageNum, long pageSize);

    void deleteOwn(Long fileId);

    /**
     * 登记转换产物为一条 FileRecord（status=CONVERTED）
     */
    FileRecord registerResult(ConvertTask task, String outputPath);

    /**
     * 清理过期文件（由定时任务调用）
     *
     * @return 清理数量
     */
    int cleanupExpired();
}
