package com.campus.convert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.convert.domain.entity.FileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecord> {

    /**
     * 查询已到期的文件（用于定时清理）
     */
    @Select("SELECT * FROM file_record WHERE status IN ('UPLOADED','USED','CONVERTED') AND expire_at IS NOT NULL AND expire_at <= #{now}")
    List<FileRecord> selectExpired(@Param("now") LocalDateTime now);
}
