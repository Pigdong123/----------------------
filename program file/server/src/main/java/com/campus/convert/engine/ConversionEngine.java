package com.campus.convert.engine;

import com.campus.convert.domain.entity.ConvertTask;

/**
 * 转换引擎 SPI：所有开源转换算法的统一接入点
 * <p>
 * 接入新算法时实现本接口并标注 @Component，通过 {@link EngineRegistry} 按引擎类型路由。
 */
public interface ConversionEngine {

    /**
     * 引擎类型
     */
    EngineType getType();

    /**
     * 支持的源扩展名
     */
    boolean supports(String sourceExt, String targetExt);

    /**
     * 执行转换
     *
     * @param task       转换任务（含源文件路径、参数）
     * @param sourcePath 源文件存储路径
     * @param targetPath 目标文件存储路径
     * @return 更新后的任务（可写入耗时、输出大小等）
     */
    ConvertTask convert(ConvertTask task, String sourcePath, String targetPath);

    /**
     * 引擎健康检查（运维端）
     */
    boolean healthCheck();

    /**
     * 是否为可选引擎：可选引擎缺失时不影响系统整体健康判定
     * （例如 CAJ/DjVu/Pages/OCR 依赖的外部工具未必安装）。
     */
    default boolean optional() {
        return false;
    }
}
