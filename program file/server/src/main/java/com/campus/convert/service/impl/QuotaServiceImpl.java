package com.campus.convert.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.entity.Quota;
import com.campus.convert.mapper.QuotaMapper;
import com.campus.convert.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 配额服务实现：惰性初始化 + 每日重置
 */
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final QuotaMapper quotaMapper;

    @Value("${campus.quota.default-daily-limit:20}")
    private int defaultDailyLimit;

    @Value("${campus.quota.default-storage-limit:1GB}")
    private String defaultStorageLimitText;

    @Override
    public Quota getOrInit(Long userId) {
        Quota quota = quotaMapper.selectOne(Wrappers.<Quota>lambdaQuery().eq(Quota::getUserId, userId));
        LocalDate today = LocalDate.now();
        if (quota == null) {
            quota = new Quota();
            quota.setUserId(userId);
            quota.setDailyLimit(defaultDailyLimit);
            quota.setUsedCount(0);
            quota.setStorageLimit(parseStorageLimit());
            quota.setUsedStorage(0L);
            quota.setResetAt(today);
            quota.setCreatedAt(LocalDateTime.now());
            try {
                quotaMapper.insert(quota);
                return quota;
            } catch (DuplicateKeyException e) {
                // 并发初始化：另一请求已插入，回读即可
                quota = quotaMapper.selectOne(Wrappers.<Quota>lambdaQuery().eq(Quota::getUserId, userId));
                if (quota == null) {
                    throw e;
                }
            }
        }
        if (quota.getResetAt() == null || quota.getResetAt().isBefore(today)) {
            quota.setUsedCount(0);
            quota.setResetAt(today);
            quotaMapper.updateById(quota);
        }
        return quota;
    }

    @Override
    public void checkAndConsume(Long userId) {
        // 先确保配额行存在且已完成跨日重置，再用单条原子 UPDATE 消费，杜绝并发超额
        getOrInit(userId);
        if (quotaMapper.consumeOne(userId) == 0) {
            throw new BizException(ResultCode.QUOTA_EXCEEDED);
        }
    }

    @Override
    public void refundOne(Long userId) {
        getOrInit(userId);
        quotaMapper.refundOne(userId);
    }

    @Override
    public void assertStorageAvailable(Long userId, long size) {
        Quota quota = getOrInit(userId);
        long used = quota.getUsedStorage() == null ? 0L : quota.getUsedStorage();
        if (used + size > quota.getStorageLimit()) {
            throw new BizException(ResultCode.STORAGE_FULL);
        }
    }

    @Override
    public void addStorageUsage(Long userId, long delta) {
        getOrInit(userId);
        // 原子增减，避免并发上传/清理时的读改写丢失更新
        quotaMapper.addStorage(userId, delta);
    }

    @Override
    public int remaining(Long userId) {
        Quota quota = getOrInit(userId);
        int used = quota.getUsedCount() == null ? 0 : quota.getUsedCount();
        return Math.max(0, quota.getDailyLimit() - used);
    }

    @Override
    public int resetDaily() {
        LocalDate today = LocalDate.now();
        Quota reset = new Quota();
        reset.setUsedCount(0);
        reset.setResetAt(today);
        return quotaMapper.update(reset,
                Wrappers.<Quota>lambdaUpdate().lt(Quota::getResetAt, today));
    }

    private long parseStorageLimit() {
        try {
            return DataSize.parse(defaultStorageLimitText).toBytes();
        } catch (Exception e) {
            return 1024L * 1024 * 1024;
        }
    }
}
