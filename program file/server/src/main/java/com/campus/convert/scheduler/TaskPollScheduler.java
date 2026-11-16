package com.campus.convert.scheduler;

import com.campus.convert.queue.TaskDispatcher;
import com.campus.convert.queue.TaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 队列轮询调度器：驱动 QUEUED 任务进入转换流程，是业务闭环的"发动机"
 * <p>
 * 每轮最多派发 {@code campus.queue.max-dispatch-per-poll} 个任务，实际并发由
 * {@code conversionExecutor} 线程池与 {@code campus.engine.max-concurrent-tasks} 约束。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskPollScheduler {

    private final TaskQueue taskQueue;
    private final TaskDispatcher taskDispatcher;

    @Value("${campus.queue.max-dispatch-per-poll:4}")
    private int maxDispatchPerPoll;

    @Scheduled(fixedDelayString = "${campus.engine.poll-interval-ms:2000}")
    public void poll() {
        long queued = taskQueue.queuedCount();
        if (queued <= 0) {
            return;
        }
        int dispatch = (int) Math.min(queued, maxDispatchPerPoll);
        for (int i = 0; i < dispatch; i++) {
            taskDispatcher.dispatch();
        }
    }
}
