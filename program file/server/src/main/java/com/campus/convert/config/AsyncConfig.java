package com.campus.convert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 转换任务异步执行器：并发上限由 campus.engine.max-concurrent-tasks 控制
     */
    @Bean("conversionExecutor")
    public Executor conversionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("conversion-");
        // 修复点：CallerRunsPolicy 属于 ThreadPoolExecutor，而非 ThreadPoolTaskExecutor
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
