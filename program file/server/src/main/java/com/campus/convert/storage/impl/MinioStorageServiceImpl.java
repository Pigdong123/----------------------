package com.campus.convert.storage.impl;

import com.campus.convert.storage.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 对象存储实现（预留接入点）
 * <p>
 * 接入步骤：
 * 1. 引入 minio-java-client 依赖
 * 2. 在 application.yml 配置 campus.storage.minio.*（endpoint/accessKey/secretKey/bucket）
 * 3. 实现各方法，替换本地存储
 */
@Service
@ConditionalOnProperty(name = "campus.storage.type", havingValue = "minio")
public class MinioStorageServiceImpl implements StorageService {

    @Override
    public String store(InputStream inputStream, String fileName, long size) {
        // TODO: 接入 MinIO SDK - minioClient.putObject(...)
        throw new UnsupportedOperationException("MinIO 存储尚未接入，见类注释");
    }

    @Override
    public InputStream load(String storagePath) {
        // TODO: 接入 MinIO SDK - minioClient.getObject(...)
        throw new UnsupportedOperationException("MinIO 存储尚未接入，见类注释");
    }

    @Override
    public void delete(String storagePath) {
        // TODO: 接入 MinIO SDK - minioClient.removeObject(...)
        throw new UnsupportedOperationException("MinIO 存储尚未接入，见类注释");
    }

    @Override
    public boolean exists(String storagePath) {
        // TODO: 接入 MinIO SDK - minioClient.statObject(...)
        throw new UnsupportedOperationException("MinIO 存储尚未接入，见类注释");
    }

    @Override
    public long usedStorageBytes() {
        // TODO: 接入 MinIO SDK - 遍历 bucket 统计
        throw new UnsupportedOperationException("MinIO 存储尚未接入，见类注释");
    }

    @Override
    public long availableStorageBytes() {
        // TODO: 接入 MinIO SDK - minioClient.getBucketInfo / 集群容量
        throw new UnsupportedOperationException("MinIO 存储尚未接入，见类注释");
    }

    @Override
    public String prepareOutputPath(String originalName, String targetExt) {
        // TODO: 返回 MinIO 对象 Key，如 output/{uuid}.{targetExt}
        throw new UnsupportedOperationException("MinIO 存储尚未接入，见类注释");
    }
}
