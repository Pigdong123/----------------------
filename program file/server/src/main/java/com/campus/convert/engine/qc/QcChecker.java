package com.campus.convert.engine.qc;

import com.campus.convert.domain.entity.ConvertTask;

/**
 * 质量检查接口（对应需求 5.4.3：音频失真、PDF 缺页、图片模糊检测）
 * <p>
 * 接入 AI 算法时实现本接口（如 AiQcChecker），通过配置切换。
 */
public interface QcChecker {

    /**
     * 对转换结果做质量检查
     *
     * @param task 已转换的任务
     * @return 是否通过质检
     */
    boolean check(ConvertTask task);

    /**
     * 质检器名称（rule/ai）
     */
    String provider();
}
