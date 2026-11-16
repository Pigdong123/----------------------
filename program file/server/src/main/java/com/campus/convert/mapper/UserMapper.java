package com.campus.convert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.convert.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
