package com.campus.convert.domain;

import com.campus.convert.domain.enums.TaskStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务状态机单元测试（需求第 9 节；无需启动 Spring 上下文）
 */
class TaskStatusEnumTest {

    @Test
    void happyPathShouldBeAllowed() {
        assertTrue(TaskStatusEnum.PENDING_VALIDATE.canTransitTo(TaskStatusEnum.QUEUED));
        assertTrue(TaskStatusEnum.QUEUED.canTransitTo(TaskStatusEnum.CONVERTING));
        assertTrue(TaskStatusEnum.CONVERTING.canTransitTo(TaskStatusEnum.QC));
        assertTrue(TaskStatusEnum.QC.canTransitTo(TaskStatusEnum.SUCCESS));
        assertTrue(TaskStatusEnum.SUCCESS.canTransitTo(TaskStatusEnum.DOWNLOADED));
        assertTrue(TaskStatusEnum.DOWNLOADED.canTransitTo(TaskStatusEnum.EXPIRED));
        assertTrue(TaskStatusEnum.EXPIRED.canTransitTo(TaskStatusEnum.DELETED));
    }

    @Test
    void retryAndCancelBranchesShouldBeAllowed() {
        assertTrue(TaskStatusEnum.CONVERTING.canTransitTo(TaskStatusEnum.FAILED));
        assertTrue(TaskStatusEnum.FAILED.canTransitTo(TaskStatusEnum.RETRYABLE));
        assertTrue(TaskStatusEnum.RETRYABLE.canTransitTo(TaskStatusEnum.QUEUED));
        assertTrue(TaskStatusEnum.QUEUED.canTransitTo(TaskStatusEnum.CANCELLED));
        assertTrue(TaskStatusEnum.PENDING_VALIDATE.canTransitTo(TaskStatusEnum.VALIDATION_FAILED));
    }

    @Test
    void illegalTransitionShouldBeRejected() {
        assertFalse(TaskStatusEnum.SUCCESS.canTransitTo(TaskStatusEnum.QUEUED));
        assertThrows(IllegalStateException.class,
                () -> TaskStatusEnum.SUCCESS.assertTransitTo(TaskStatusEnum.CONVERTING));
    }
}
