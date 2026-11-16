package com.campus.convert.service;

import java.util.List;
import java.util.Map;

/**
 * 统计服务（管理端/运维端报表）
 */
public interface StatsService {

    Map<String, Object> overview();

    List<Map<String, Object>> byFormat(int days);

    List<Map<String, Object>> byStatus();
}
