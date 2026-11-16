package com.campus.convert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.convert.domain.entity.FormatTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FormatTemplateMapper extends BaseMapper<FormatTemplate> {

    @Select("SELECT * FROM format_template WHERE source_ext = #{sourceExt} AND enabled = 1")
    List<FormatTemplate> selectBySourceExt(@Param("sourceExt") String sourceExt);

    /**
     * 按源/目标扩展名精确解析模板（任务路由使用）
     */
    @Select("SELECT * FROM format_template WHERE source_ext = #{sourceExt} AND target_ext = #{targetExt} LIMIT 1")
    FormatTemplate selectBySourceExtAndTarget(@Param("sourceExt") String sourceExt,
                                              @Param("targetExt") String targetExt);
}
