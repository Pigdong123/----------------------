package com.campus.convert.scheduler;

import com.campus.convert.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 存储监控调度器：周期采样存储占用并在低于阈值时告警（运维端）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageMonitorScheduler {

    private final StorageService storageService;

    @Value("${campus.storage.alert-threshold-bytes:1073741824}")
    private long alertThresholdBytes;

    @Scheduled(fixedDelayString = "${campus.storage.monitor-interval-ms:600000}")
    public void monitor() {
        long used = storageService.usedStorageBytes();
        long available = storageService.availableStorageBytes();
        if (available >= 0 && available < alertThresholdBytes) {
            log.warn("存储告警: used={}B, available={}B, threshold={}B", used, available, alertThresholdBytes);
        }
    }
}
