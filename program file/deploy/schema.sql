-- 校园课程资源格式适配与转换服务系统 - 数据库 DDL
-- 对应需求文档第 8 节"核心数据对象初步设计"

CREATE DATABASE IF NOT EXISTS campus_convert DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_convert;

-- 用户表（对应 User：id, username, password_hash, role, quota, created_at）
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT/TEACHER/COURSE_ADMIN/OPS/SYS_ADMIN',
    quota INT NOT NULL DEFAULT 20 COMMENT '每日转换配额',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) COMMENT '用户表';

-- 文件记录表（对应 File：id, user_id, original_name, storage_path, mime, size, hash, status, expire_at）
CREATE TABLE file_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '上传用户',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_path VARCHAR(500) NOT NULL COMMENT '存储路径',
    ext VARCHAR(20) COMMENT '扩展名',
    mime VARCHAR(100) COMMENT 'MIME 类型',
    size BIGINT NOT NULL DEFAULT 0 COMMENT '大小（字节）',
    hash VARCHAR(64) COMMENT 'SHA-256 哈希，用于秒传与校验',
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED' COMMENT '状态：UPLOADED/USED/CONVERTED/EXPIRED/DELETED',
    expire_at DATETIME COMMENT '过期时间，到期自动清理',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_expire (status, expire_at)
) COMMENT '文件记录表';

-- 转换任务表（对应 ConvertTask：id, user_id, source_file_id, target_format, params, status, priority, error_msg, created_at, finished_at）
CREATE TABLE convert_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_file_id BIGINT NOT NULL COMMENT '源文件',
    target_format VARCHAR(20) NOT NULL COMMENT '目标格式',
    params JSON COMMENT '转换参数（码率/清晰度/质量等）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VALIDATE' COMMENT '任务状态机：PENDING_VALIDATE/VALIDATION_FAILED/QUEUED/CONVERTING/QC/SUCCESS/FAILED/RETRYABLE/DOWNLOADED/EXPIRED/DELETED/CANCELLED',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级，数值越大越优先',
    error_msg VARCHAR(1000) COMMENT '失败原因（用户可读）',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    result_file_id BIGINT COMMENT '转换产物文件ID（成功后登记）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME COMMENT '开始转换时间',
    finished_at DATETIME COMMENT '完成时间',
    INDEX idx_user_status (user_id, status),
    INDEX idx_status_priority (status, priority),
    INDEX idx_created (created_at)
) COMMENT '转换任务表';

-- 格式模板表（对应 FormatTemplate：id, source_ext, target_ext, engine, params_schema, enabled）
CREATE TABLE format_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_ext VARCHAR(20) NOT NULL COMMENT '源扩展名',
    target_ext VARCHAR(20) NOT NULL COMMENT '目标扩展名',
    engine VARCHAR(30) NOT NULL COMMENT '转换引擎：FFMPEG/LIBREOFFICE/PANDOC/IMAGEMAGICK',
    params_schema JSON COMMENT '参数 Schema，前端动态渲染参数表单',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_src_tgt (source_ext, target_ext)
) COMMENT '格式模板表';

-- 反馈表（任务反馈 + 通用反馈；task_id 为空表示通用反馈）
CREATE TABLE feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NULL COMMENT '关联任务（通用反馈可为空）',
    user_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'OTHER' COMMENT 'BUG/FEATURE/CONTENT/EXPERIENCE/OTHER',
    title VARCHAR(120) COMMENT '反馈标题',
    rating TINYINT NULL COMMENT '评分 1-5（任务反馈可选）',
    comment VARCHAR(1000) NOT NULL COMMENT '反馈内容',
    contact VARCHAR(100) COMMENT '联系方式（可选）',
    anonymous TINYINT NOT NULL DEFAULT 0 COMMENT '是否匿名',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/CLOSED',
    admin_reply VARCHAR(1000) COMMENT '管理员回复',
    replied_by BIGINT COMMENT '回复管理员',
    replied_at DATETIME COMMENT '回复时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task (task_id),
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_category (category),
    INDEX idx_created (created_at)
) COMMENT '反馈表';

-- 审计日志表（对应 AuditLog：id, user_id, action, target, ip, created_at）
CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '操作用户（脱敏存储）',
    action VARCHAR(50) NOT NULL COMMENT '操作类型',
    target VARCHAR(200) COMMENT '操作对象',
    ip VARCHAR(50) COMMENT 'IP（脱敏）',
    detail VARCHAR(1000) COMMENT '操作详情（脱敏）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_created (created_at)
) COMMENT '审计日志表';

-- 配额表（对应 Quota：id, user_id, daily_limit, used_count, reset_at）
CREATE TABLE quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    daily_limit INT NOT NULL DEFAULT 20 COMMENT '每日次数上限',
    used_count INT NOT NULL DEFAULT 0 COMMENT '今日已用',
    storage_limit BIGINT NOT NULL DEFAULT 1073741824 COMMENT '总存储上限（字节）',
    used_storage BIGINT NOT NULL DEFAULT 0 COMMENT '已用存储（字节）',
    reset_at DATE NOT NULL COMMENT '配额重置日期',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '配额表';

-- 通知表（对应 Notification：id, user_id, task_id, type, content, read_flag, created_at）
CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    task_id BIGINT COMMENT '关联任务',
    type VARCHAR(30) NOT NULL COMMENT '类型：TASK_SUCCESS/TASK_FAILED/SYSTEM',
    content VARCHAR(500) NOT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, read_flag)
) COMMENT '通知表';

-- 初始化格式模板（第一期范围：docx/pptx→pdf，flac/wav/m4a→mp3，png/jpg→webp）
INSERT INTO format_template (source_ext, target_ext, engine, params_schema, enabled) VALUES
('docx', 'pdf', 'LIBREOFFICE', JSON_OBJECT('clarity', JSON_OBJECT('type', 'enum', 'default', 'standard', 'enum', '["low","standard","high"]')), 1),
('pptx', 'pdf', 'LIBREOFFICE', JSON_OBJECT('clarity', JSON_OBJECT('type', 'enum', 'default', 'standard', 'enum', '["low","standard","high"]')), 1),
('doc',  'pdf', 'LIBREOFFICE', JSON_OBJECT('clarity', JSON_OBJECT('type', 'enum', 'default', 'standard', 'enum', '["low","standard","high"]')), 1),
('ppt',  'pdf', 'LIBREOFFICE', JSON_OBJECT('clarity', JSON_OBJECT('type', 'enum', 'default', 'standard', 'enum', '["low","standard","high"]')), 1),
('flac', 'mp3', 'FFMPEG', JSON_OBJECT('bitrate', JSON_OBJECT('type', 'enum', 'default', '192k', 'enum', '["128k","192k","256k","320k"]')), 1),
('wav',  'mp3', 'FFMPEG', JSON_OBJECT('bitrate', JSON_OBJECT('type', 'enum', 'default', '192k', 'enum', '["128k","192k","256k","320k"]')), 1),
('m4a',  'mp3', 'FFMPEG', JSON_OBJECT('bitrate', JSON_OBJECT('type', 'enum', 'default', '192k', 'enum', '["128k","192k","256k","320k"]')), 1),
('png',  'webp', 'IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type', 'integer', 'default', 80, 'min', 10, 'max', 100)), 1),
('jpg',  'webp', 'IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type', 'integer', 'default', 80, 'min', 10, 'max', 100)), 1),
('jpeg', 'webp', 'IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type', 'integer', 'default', 80, 'min', 10, 'max', 100)), 1);

-- ============ 多格式扩展（第二批：视频 / NCM / HEIC / Pages / CAJ / DjVu / 字幕 / OCR） ============
INSERT INTO format_template (source_ext, target_ext, engine, params_schema, enabled) VALUES
-- 视频：m4s / MOV / HEVC / MKV → MP4
('m4s',  'mp4', 'FFMPEG', JSON_OBJECT('mode', JSON_OBJECT('type','enum','default','auto','enum','["auto","remux","transcode"]'), 'crf', JSON_OBJECT('type','integer','default',23,'min',18,'max',32)), 1),
('mov',  'mp4', 'FFMPEG', JSON_OBJECT('mode', JSON_OBJECT('type','enum','default','auto','enum','["auto","remux","transcode"]'), 'crf', JSON_OBJECT('type','integer','default',23,'min',18,'max',32)), 1),
('hevc', 'mp4', 'FFMPEG', JSON_OBJECT('mode', JSON_OBJECT('type','enum','default','auto','enum','["auto","remux","transcode"]'), 'crf', JSON_OBJECT('type','integer','default',23,'min',18,'max',32)), 1),
('mkv',  'mp4', 'FFMPEG', JSON_OBJECT('mode', JSON_OBJECT('type','enum','default','auto','enum','["auto","remux","transcode"]'), 'crf', JSON_OBJECT('type','integer','default',23,'min',18,'max',32)), 1),
-- NCM → MP3 / FLAC
('ncm',  'mp3', 'NCM', JSON_OBJECT('bitrate', JSON_OBJECT('type','enum','default','320k','enum','["128k","192k","256k","320k"]')), 1),
('ncm',  'flac','NCM', NULL, 1),
-- HEIC / HEIF → JPG / PNG / WebP
('heic', 'jpg', 'IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',90,'min',10,'max',100)), 1),
('heic', 'png', 'IMAGEMAGICK', NULL, 1),
('heic', 'webp','IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',80,'min',10,'max',100)), 1),
('heif', 'jpg', 'IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',90,'min',10,'max',100)), 1),
-- Apple Pages → PDF
('pages','pdf', 'PAGES', NULL, 1),
-- CAJ → PDF
('caj',  'pdf', 'CAJ', NULL, 1),
-- DjVu → PDF
('djvu', 'pdf', 'DJVU', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',80,'min',10,'max',100)), 1),
('djv',  'pdf', 'DJVU', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',80,'min',10,'max',100)), 1),
-- 字幕互转
('srt',  'vtt', 'SUBTITLE', NULL, 1),
('srt',  'ass', 'SUBTITLE', NULL, 1),
('vtt',  'srt', 'SUBTITLE', NULL, 1),
('vtt',  'ass', 'SUBTITLE', NULL, 1),
('ass',  'srt', 'SUBTITLE', NULL, 1),
('ass',  'vtt', 'SUBTITLE', NULL, 1),
('ssa',  'srt', 'SUBTITLE', NULL, 1),
('ssa',  'vtt', 'SUBTITLE', NULL, 1),
-- 公式 / OCR
('png',  'txt',   'FORMULA_OCR', NULL, 1),
('jpg',  'txt',   'FORMULA_OCR', NULL, 1),
('jpeg', 'txt',   'FORMULA_OCR', NULL, 1),
('png',  'latex', 'FORMULA_OCR', NULL, 1),
('jpg',  'latex', 'FORMULA_OCR', NULL, 1),
('heic', 'txt',   'FORMULA_OCR', NULL, 1);
