package com.campus.convert.scheduler;

import com.campus.convert.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 配额重置调度器：每日零点重置所有用户的每日转换配额
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuotaResetScheduler {

    private final QuotaService quotaService;

    @Scheduled(cron = "${campus.quota.reset-cron:0 0 0 * * ?}")
    public void resetDaily() {
        int count = quotaService.resetDaily();
        log.info("每日配额重置完成: users={}", count);
    }
}
