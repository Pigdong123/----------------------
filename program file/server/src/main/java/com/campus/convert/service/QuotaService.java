package com.campus.convert.service;

import com.campus.convert.domain.entity.Quota;

/**
 * 配额服务：每日次数、存储总量
 */
public interface QuotaService {

    Quota getOrInit(Long userId);

    /**
     * 校验并消费一次每日转换配额
     *
     * @throws com.campus.convert.common.BizException 配额用尽时抛出
     */
    void checkAndConsume(Long userId);

    /**
     * 归还一次已消费的每日配额（用于任务创建失败回滚）
     */
    void refundOne(Long userId);

    void assertStorageAvailable(Long userId, long size);

    void addStorageUsage(Long userId, long delta);

    int remaining(Long userId);

    /**
     * 重置所有用户当日配额（由定时任务调用）
     *
     * @return 重置数量
     */
    int resetDaily();
}
