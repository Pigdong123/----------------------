package com.campus.convert.queue;

import com.campus.convert.domain.entity.ConvertTask;

import java.util.List;

/**
 * 转换任务队列接口
 * <p>
 * 替换实现：Celery / BullMQ / RQ（需求文档第 7 节）
 */
public interface TaskQueue {

    /**
     * 任务入队（按优先级）
     */
    void enqueue(ConvertTask task);

    /**
     * 出队一个任务（阻塞或立即返回）
     */
    ConvertTask dequeue();

    /**
     * 取消排队中的任务
     */
    boolean cancel(Long taskId);

    /**
     * 当前排队数量（运维监控）
     */
    long queuedCount();

    /**
     * 查看队列快照（运维监控）
     */
    List<Long> snapshot();
}
