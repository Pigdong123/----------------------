-- ============================================================
-- 增量迁移脚本：多格式转换 + 完整反馈模块（2026-10-27）
-- 适用：已按 schema.sql 初始化过的数据库；新库直接执行 schema.sql 即可
-- ============================================================
USE campus_convert;

-- ---------- 1. 反馈表升级（任务反馈 + 通用反馈 + 管理回复） ----------
-- 兼容历史数据：先把空评语补成空串，再改为 NOT NULL
UPDATE feedback SET comment = '' WHERE comment IS NULL;

ALTER TABLE feedback
    MODIFY COLUMN task_id BIGINT NULL COMMENT '关联任务（通用反馈可为空）',
    MODIFY COLUMN comment VARCHAR(1000) NOT NULL COMMENT '反馈内容',
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'OTHER' COMMENT 'BUG/FEATURE/CONTENT/EXPERIENCE/OTHER' AFTER user_id,
    ADD COLUMN title VARCHAR(120) NULL COMMENT '反馈标题' AFTER category,
    ADD COLUMN contact VARCHAR(100) NULL COMMENT '联系方式' AFTER comment,
    ADD COLUMN anonymous TINYINT NOT NULL DEFAULT 0 COMMENT '是否匿名' AFTER contact,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/CLOSED' AFTER anonymous,
    ADD COLUMN admin_reply VARCHAR(1000) NULL COMMENT '管理员回复' AFTER status,
    ADD COLUMN replied_by BIGINT NULL COMMENT '回复管理员' AFTER admin_reply,
    ADD COLUMN replied_at DATETIME NULL COMMENT '回复时间' AFTER replied_by,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD INDEX idx_user (user_id),
    ADD INDEX idx_status (status),
    ADD INDEX idx_category (category),
    ADD INDEX idx_created (created_at);

-- ---------- 2. 多格式转换模板 ----------
INSERT IGNORE INTO format_template (source_ext, target_ext, engine, params_schema, enabled) VALUES
('m4s',  'mp4', 'FFMPEG', JSON_OBJECT('mode', JSON_OBJECT('type','enum','default','auto','enum','["auto","remux","transcode"]'), 'crf', JSON_OBJECT('type','integer','default',23,'min',18,'max',32)), 1),
('mov',  'mp4', 'FFMPEG', JSON_OBJECT('mode', JSON_OBJECT('type','enum','default','auto','enum','["auto","remux","transcode"]'), 'crf', JSON_OBJECT('type','integer','default',23,'min',18,'max',32)), 1),
('hevc', 'mp4', 'FFMPEG', JSON_OBJECT('mode', JSON_OBJECT('type','enum','default','auto','enum','["auto","remux","transcode"]'), 'crf', JSON_OBJECT('type','integer','default',23,'min',18,'max',32)), 1),
('mkv',  'mp4', 'FFMPEG', JSON_OBJECT('mode', JSON_OBJECT('type','enum','default','auto','enum','["auto","remux","transcode"]'), 'crf', JSON_OBJECT('type','integer','default',23,'min',18,'max',32)), 1),
('ncm',  'mp3', 'NCM', JSON_OBJECT('bitrate', JSON_OBJECT('type','enum','default','320k','enum','["128k","192k","256k","320k"]')), 1),
('ncm',  'flac','NCM', NULL, 1),
('heic', 'jpg', 'IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',90,'min',10,'max',100)), 1),
('heic', 'png', 'IMAGEMAGICK', NULL, 1),
('heic', 'webp','IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',80,'min',10,'max',100)), 1),
('heif', 'jpg', 'IMAGEMAGICK', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',90,'min',10,'max',100)), 1),
('pages','pdf', 'PAGES', NULL, 1),
('caj',  'pdf', 'CAJ', NULL, 1),
('djvu', 'pdf', 'DJVU', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',80,'min',10,'max',100)), 1),
('djv',  'pdf', 'DJVU', JSON_OBJECT('quality', JSON_OBJECT('type','integer','default',80,'min',10,'max',100)), 1),
('srt',  'vtt', 'SUBTITLE', NULL, 1),
('srt',  'ass', 'SUBTITLE', NULL, 1),
('vtt',  'srt', 'SUBTITLE', NULL, 1),
('vtt',  'ass', 'SUBTITLE', NULL, 1),
('ass',  'srt', 'SUBTITLE', NULL, 1),
('ass',  'vtt', 'SUBTITLE', NULL, 1),
('ssa',  'srt', 'SUBTITLE', NULL, 1),
('ssa',  'vtt', 'SUBTITLE', NULL, 1),
('png',  'txt',   'FORMULA_OCR', NULL, 1),
('jpg',  'txt',   'FORMULA_OCR', NULL, 1),
('jpeg', 'txt',   'FORMULA_OCR', NULL, 1),
('png',  'latex', 'FORMULA_OCR', NULL, 1),
('jpg',  'latex', 'FORMULA_OCR', NULL, 1),
('heic', 'txt',   'FORMULA_OCR', NULL, 1);
