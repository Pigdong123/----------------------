export const ROLE = {
  STUDENT: '学生',
  TEACHER: '教师/助教',
  COURSE_ADMIN: '课程管理员/教务',
  OPS: '系统运维',
  SYS_ADMIN: '系统管理员'
}

export const TASK_STATUS = {
  PENDING_VALIDATE: '待校验',
  VALIDATION_FAILED: '校验失败',
  QUEUED: '排队中',
  CONVERTING: '转换中',
  QC: '质检中',
  SUCCESS: '成功',
  FAILED: '失败',
  RETRYABLE: '可重试',
  DOWNLOADED: '已下载',
  EXPIRED: '已过期',
  DELETED: '已删除',
  CANCELLED: '已取消'
}

export const STATUS_TYPE = {
  PENDING_VALIDATE: 'info',
  VALIDATION_FAILED: 'danger',
  QUEUED: 'warning',
  CONVERTING: 'primary',
  QC: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
  RETRYABLE: 'warning',
  DOWNLOADED: 'success',
  EXPIRED: 'info',
  DELETED: 'info',
  CANCELLED: 'info'
}

export const FEEDBACK_CATEGORY = {
  BUG: '功能缺陷',
  FEATURE: '功能建议',
  CONTENT: '内容问题',
  EXPERIENCE: '使用体验',
  OTHER: '其他'
}

export const FEEDBACK_STATUS = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭'
}

export const FEEDBACK_STATUS_TYPE = {
  PENDING: 'warning',
  PROCESSING: 'primary',
  RESOLVED: 'success',
  CLOSED: 'info'
}

export const FIRST_PHASE_FORMATS = {
  'docx': ['pdf'],
  'doc': ['pdf'],
  'pptx': ['pdf'],
  'ppt': ['pdf'],
  'flac': ['mp3'],
  'wav': ['mp3'],
  'm4a': ['mp3'],
  'png': ['webp'],
  'jpg': ['webp'],
  'jpeg': ['webp']
}
