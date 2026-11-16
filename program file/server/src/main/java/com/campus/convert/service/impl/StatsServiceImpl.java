package com.campus.convert.service.impl;

import com.campus.convert.mapper.ConvertTaskMapper;
import com.campus.convert.queue.TaskQueue;
import com.campus.convert.service.StatsService;
import com.campus.convert.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final ConvertTaskMapper convertTaskMapper;
    private final TaskQueue taskQueue;
    private final StorageService storageService;

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("statusCounts", convertTaskMapper.countGroupByStatus());
        map.put("queuedCount", taskQueue.queuedCount());
        map.put("tasksLast7d", convertTaskMapper.countSince(LocalDateTime.now().minusDays(7)));
        map.put("storageUsedBytes", storageService.usedStorageBytes());
        map.put("storageAvailableBytes", storageService.availableStorageBytes());
        return map;
    }

    @Override
    public List<Map<String, Object>> byFormat(int days) {
        return convertTaskMapper.countGroupByFormat(LocalDateTime.now().minusDays(days));
    }

    @Override
    public List<Map<String, Object>> byStatus() {
        return convertTaskMapper.countGroupByStatus();
    }
}
