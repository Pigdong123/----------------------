package com.campus.convert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.convert.domain.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Update("UPDATE notification SET read_flag = 1 WHERE user_id = #{userId} AND read_flag = 0")
    int markAllRead(@Param("userId") Long userId);
}
