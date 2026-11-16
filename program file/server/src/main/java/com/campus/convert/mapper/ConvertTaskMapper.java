package com.campus.convert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.convert.domain.entity.ConvertTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ConvertTaskMapper extends BaseMapper<ConvertTask> {

    /**
     * 按状态统计任务数（运维监控）
     */
    @Select("SELECT status AS status, COUNT(*) AS count FROM convert_task GROUP BY status")
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 按目标格式统计转换量（管理端报表）
     */
    @Select("SELECT target_format AS targetFormat, COUNT(*) AS count FROM convert_task " +
            "WHERE created_at >= #{since} GROUP BY target_format")
    List<Map<String, Object>> countGroupByFormat(@Param("since") java.time.LocalDateTime since);

    /**
     * 查询排队中的任务（按优先级排序）
     */
    @Select("SELECT * FROM convert_task WHERE status = 'QUEUED' ORDER BY priority DESC, created_at ASC")
    List<ConvertTask> selectQueuedTasks();

    /**
     * 查询已成功且已过期、需要归档为 EXPIRED 的任务
     */
    @Select("SELECT * FROM convert_task WHERE status IN ('SUCCESS','DOWNLOADED') AND finished_at IS NOT NULL " +
            "AND finished_at <= #{before}")
    List<ConvertTask> selectExpirable(@Param("before") java.time.LocalDateTime before);

    /**
     * 统计指定时间范围内的任务量（管理端报表）
     */
    @Select("SELECT COUNT(*) FROM convert_task WHERE created_at >= #{since}")
    long countSince(@Param("since") java.time.LocalDateTime since);

    /**
     * 乐观状态流转：仅当数据库当前状态仍为 expected 时才更新。
     * <p>
     * 修复点：原实现"先查后改"，两个 worker 并发时可能同时把 QUEUED 改成 CONVERTING，
     * 造成重复执行。CAS 更新保证同一状态只会被消费一次。
     *
     * @return 受影响行数，0 表示状态已被其他线程改变（或已不满足条件）
     */
    @Update("""
            <script>
            UPDATE convert_task
            <set>
              <if test="target != null">status = #{target},</if>
              <if test="startedAt != null">started_at = #{startedAt},</if>
              <if test="finishedAt != null">finished_at = #{finishedAt},</if>
              <if test="resultFileId != null">result_file_id = #{resultFileId},</if>
              <if test="errorMsg != null">error_msg = #{errorMsg},</if>
              <if test="retryCount != null">retry_count = #{retryCount},</if>
              <if test="clearError">error_msg = NULL,</if>
              <if test="clearResult">result_file_id = NULL,</if>
            </set>
            WHERE id = #{id} AND status = #{expected}
            </script>
            """)
    int casUpdate(@Param("id") Long id,
                  @Param("expected") String expected,
                  @Param("target") String target,
                  @Param("startedAt") LocalDateTime startedAt,
                  @Param("finishedAt") LocalDateTime finishedAt,
                  @Param("resultFileId") Long resultFileId,
                  @Param("errorMsg") String errorMsg,
                  @Param("retryCount") Integer retryCount,
                  @Param("clearError") boolean clearError,
                  @Param("clearResult") boolean clearResult);

    /**
     * 统计某源文件上仍在进行中的任务数（防止删除正在转换的源文件）
     */
    @Select("SELECT COUNT(*) FROM convert_task WHERE source_file_id = #{fileId} " +
            "AND status IN ('PENDING_VALIDATE','QUEUED','CONVERTING','QC','RETRYABLE')")
    long countActiveBySourceFile(@Param("fileId") Long fileId);
}
