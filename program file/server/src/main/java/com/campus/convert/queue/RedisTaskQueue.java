package com.campus.convert.queue;

import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.mapper.ConvertTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 Redis List 的任务队列实现（默认）
 * <p>
 * 使用有序结构模拟优先级：KEY 为队列，元素为 "taskId:priority"，按 score 排序。
 */
@Component
@ConditionalOnProperty(name = "campus.queue.provider", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RedisTaskQueue implements TaskQueue {

    private final StringRedisTemplate redisTemplate;
    private final ConvertTaskMapper convertTaskMapper;

    @org.springframework.beans.factory.annotation.Value("${campus.queue.redis-key:campus:task:queue}")
    private String queueKey;

    @Override
    public void enqueue(ConvertTask task) {
        String member = task.getId() + ":" + (task.getPriority() == null ? 0 : task.getPriority());
        // score = priority * 1e12 - timestamp，保证高优先级先出、同优先级先进先出
        double score = (task.getPriority() == null ? 0 : task.getPriority()) * 1_000_000_000_000.0
                - System.currentTimeMillis() / 1000.0;
        redisTemplate.opsForZSet().add(queueKey, member, score);
        log.info("任务入队: taskId={}, targetFormat={}", task.getId(), task.getTargetFormat());
    }

    @Override
    public ConvertTask dequeue() {
        var set = redisTemplate.opsForZSet().popMax(queueKey, 1);
        if (set == null || set.isEmpty()) {
            return null;
        }
        String member = set.iterator().next().getValue();
        if (member == null) {
            return null;
        }
        Long taskId = Long.valueOf(member.split(":")[0]);
        return convertTaskMapper.selectById(taskId);
    }

    @Override
    public boolean cancel(Long taskId) {
        // 遍历队列移除该任务（队列规模小可接受；大规模可改用倒排索引）
        var members = redisTemplate.opsForZSet().range(queueKey, 0, -1);
        if (members == null) {
            return false;
        }
        for (String member : members) {
            if (member.startsWith(taskId + ":")) {
                return Boolean.TRUE.equals(redisTemplate.opsForZSet().remove(queueKey, member));
            }
        }
        return false;
    }

    @Override
    public long queuedCount() {
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size == null ? 0 : size;
    }

    @Override
    public List<Long> snapshot() {
        var members = redisTemplate.opsForZSet().range(queueKey, 0, -1);
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .map(m -> Long.valueOf(m.split(":")[0]))
                .collect(Collectors.toList());
    }
}
