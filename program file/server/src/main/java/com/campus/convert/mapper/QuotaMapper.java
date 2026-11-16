package com.campus.convert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.convert.domain.entity.Quota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuotaMapper extends BaseMapper<Quota> {

    /**
     * 原子消费一次每日配额：仅当 used_count < daily_limit 时才 +1
     * <p>
     * 修复点：原实现采用"先读后写"，并发下多个请求可同时通过校验造成超额。
     *
     * @return 受影响行数，0 表示配额已用尽
     */
    @Update("UPDATE quota SET used_count = used_count + 1 " +
            "WHERE user_id = #{userId} AND used_count < daily_limit")
    int consumeOne(@Param("userId") Long userId);

    /**
     * 原子增减存储占用，并保证不会出现负数
     */
    @Update("UPDATE quota SET used_storage = GREATEST(0, used_storage + #{delta}) " +
            "WHERE user_id = #{userId}")
    int addStorage(@Param("userId") Long userId, @Param("delta") long delta);

    /**
     * 归还一次配额（任务创建失败时回滚已消费的配额）
     */
    @Update("UPDATE quota SET used_count = GREATEST(0, used_count - 1) WHERE user_id = #{userId}")
    int refundOne(@Param("userId") Long userId);
}
