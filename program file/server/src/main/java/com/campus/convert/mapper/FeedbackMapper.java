package com.campus.convert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.convert.domain.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    @Select("SELECT category, COUNT(*) AS cnt FROM feedback GROUP BY category")
    List<Map<String, Object>> countByCategory();

    @Select("SELECT status, COUNT(*) AS cnt FROM feedback GROUP BY status")
    List<Map<String, Object>> countByStatus();

    @Select("SELECT AVG(rating) FROM feedback WHERE rating IS NOT NULL")
    Double avgRating();
}
