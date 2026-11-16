package com.campus.convert.storage;

import java.io.InputStream;

public interface StorageService {

    /**
     * 保存上传文件
     *
     * @param inputStream 文件流
     * @param fileName    存储文件名（不含路径）
     * @param size        文件大小
     * @return 存储路径
     */
    String store(InputStream inputStream, String fileName, long size);

    /**
     * 读取文件
     */
    InputStream load(String storagePath);

    /**
     * 删除文件
     */
    void delete(String storagePath);

    /**
     * 判断文件是否存在
     */
    boolean exists(String storagePath);

    /**
     * 获取当前已用存储空间（字节）
     */
    long usedStorageBytes();

    /**
     * 获取剩余可用空间（字节），用于运维监控与存储不足预警
     */
    long availableStorageBytes();

    /**
     * 为转换产物分配一个可写路径（本地存储会创建父目录；对象存储返回对象 Key）
     *
     * @param originalName 源文件原始名（用于保留可读性，可忽略）
     * @param targetExt    目标扩展名（不含点）
     * @return 引擎应写出的存储路径
     */
    String prepareOutputPath(String originalName, String targetExt);
}
