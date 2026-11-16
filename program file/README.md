帮我

# 校园课程资源格式适配与转换服务系统（CampusFormat）

面向校园课程资源的格式适配与转换服务平台：限定合法格式，覆盖文档、音频、图片三类转换，提供"上传→校验→排队→转换→质检→下载→反馈→清理"完整闭环，支持学生、教师/助教、课程管理员、运维、系统管理员五类角色。

## 技术栈

| 层次       | 技术                                                                                                |
| ---------- | --------------------------------------------------------------------------------------------------- |
| 前端       | Vue 3 + Vite + Vue Router + Pinia + Element Plus + Axios                                            |
| 后端       | Spring Boot 3 (Java 17) + Spring Security + JWT + MyBatis-Plus                                      |
| 数据库     | MySQL 8                                                                                             |
| 缓存与队列 | Redis（任务队列、配额计数、下载令牌）                                                               |
| 存储       | 本地文件系统（MinIO 接入点已预留）                                                                  |
| 转换引擎   | ffmpeg（音频）、LibreOffice/Pandoc（文档）、ImageMagick（图片），通过 `ConversionEngine` SPI 接入 |
| AI 增强    | 大模型 API（预留），本地规则引擎兜底，全部可降级                                                    |
| 部署       | Docker + Docker Compose                                                                             |

## 目录结构

```
program file/
├── deploy/                  # 部署：docker-compose、数据库 DDL、环境变量模板
├── server/                  # Spring Boot 后端
│   └── src/
│       ├── main/java/com/campus/convert/
│       │   ├── common/      # 统一响应 Result、异常 BizException、上下文 BaseContext
│       │   ├── config/      # Security、MyBatis-Plus 分页、异步、异常、Knife4j、CORS
│       │   ├── controller/  # REST 接口：auth/file/task/feedback/notification/ai/admin/ops
│       │   ├── domain/      # entity、enums、dto
│       │   ├── mapper/      # MyBatis-Plus Mapper
│       │   ├── service/     # 业务层接口 + impl（用户/文件/任务/模板/反馈/通知/配额/统计/审计）
│       │   ├── queue/       # 任务队列（Redis ZSet 优先级）+ TaskDispatcher
│       │   ├── engine/      # 转换引擎 SPI + EngineRegistry + qc（规则/AI 质检）
│       │   ├── storage/     # 文件存储 SPI（local / MinIO 接入点）
│       │   ├── ai/          # AI 增强（rule 默认 / llm 接入点），可降级
│       │   ├── security/     # JWT 工具与过滤器
│       │   ├── scheduler/   # 队列轮询、过期清理、配额重置、存储监控
│       │   └── util/        # 文件哈希、类型识别
│       └── test/java/       # 状态机等单元测试
└── web/                     # Vue 3 前端
    └── src/
        ├── api/             # Axios 封装与接口模块
        ├── stores/          # Pinia 状态（user/task）
        ├── router/          # 路由与登录/角色守卫
        ├── views/           # 登录/上传/任务/历史/反馈/AI/管理端/运维端
        ├── components/      # 上传器、格式选择器、参数表单、状态徽章
        └── utils/           # 常量与格式化
```

## 业务闭环（框架已打通，算法待接入）

```
登录 → 上传文件 → 校验格式/大小/安全 → 选择目标格式与参数 → 创建转换任务
     → 任务入队(Redis ZSet) → TaskPollScheduler 轮询 → TaskDispatcher 取出
     → EngineRegistry 路由引擎执行 → QcChecker 质检 → 登记产物 FileRecord
     → 通知 + 用户下载 → 反馈评分 → 定时过期清理 → 管理端统计与审计
```

关键接线点：

- `scheduler/TaskPollScheduler` 定时消费 `queue/TaskQueue`；
- `queue/TaskDispatcher` 驱动 `CONVERTING → QC → SUCCESS/FAILED` 状态机，失败可重试；
- `engine/EngineRegistry` 依据 `format_template` 解析模板并分配产物路径；
- `service/impl/TaskServiceImpl` 实现 `domain/enums/TaskStatusEnum` 定义的全部合法状态流转。

> 转换引擎（`engine/impl/*`）、MinIO（`storage/impl/MinioStorageServiceImpl`）、大模型（`ai/impl/LlmAiServiceImpl`）均为占位实现，按类注释接入开源算法/服务即可，不影响框架编译与闭环打通。

## 快速开始

1. 复制 `deploy/.env.example` 为 `deploy/.env` 并按需修改。
2. `docker compose -f deploy/docker-compose.yml up -d` 启动 MySQL、Redis、应用与前端。
3. 导入 `deploy/schema.sql`（compose 已自动执行）。
4. 前端开发（需 Node 18+）：`cd web && npm install && npm run dev`。
5. 后端开发（需 JDK 17 + Maven）：`cd server && mvn spring-boot:run`。
6. 接口文档：启动后访问 `http://localhost:8080/api/doc.html`。

> 初始化提示：系统**第一个注册的用户自动成为 `SYS_ADMIN`**，可用其完成角色/模板/配额配置。

## 配置项（application.yml）

| 配置                                   | 说明                          |
| -------------------------------------- | ----------------------------- |
| `campus.queue.provider`              | 队列实现，默认 redis          |
| `campus.engine.poll-interval-ms`     | 队列轮询间隔，默认 2000ms     |
| `campus.queue.max-dispatch-per-poll` | 每轮最多派发任务数            |
| `campus.engine.qc.provider`          | 质检器：rule（默认）/ ai      |
| `campus.ai.provider`                 | AI：rule（默认，离线）/ llm   |
| `campus.ai.enabled`                  | AI 总开关，关闭不影响核心转换 |
| `campus.file.allowed-extensions`     | 允许的源格式白名单            |
| `campus.file.expire-days`            | 文件/任务过期天数             |

## 开源算法/服务接入点

- 音频引擎：`engine/impl/AudioConversionEngine.java`（ffmpeg）
- 文档引擎：`engine/impl/DocumentConversionEngine.java`（LibreOffice / Pandoc）
- 图片引擎：`engine/impl/ImageConversionEngine.java`（ImageMagick）
- 对象存储：`storage/impl/MinioStorageServiceImpl.java`（MinIO）
- 大模型服务：`ai/impl/LlmAiServiceImpl.java`（开关：`campus.ai.provider=llm`）
- 质检算法：`engine/qc/AiQcChecker.java`（音频失真 / PDF 缺页 / 图片模糊检测）

## 核心格式范围（第一期）

- 文档：doc/docx/ppt/pptx → pdf
- 音频：flac/wav/m4a → mp3
- 图片：png/jpg/jpeg → webp

## 已修补的原有框架漏洞

1. `TaskDispatcher` 引用了不存在的 `service.TaskService` / `service.NotificationService`（无法编译）→ 已补全整个 service 层。
2. `EngineRegistry.execute` 存在签名不匹配且源/产物路径返回空串 → 已改为按模板路由并借助 `StorageService.prepareOutputPath` 分配产物路径。
3. 任务缺少产物关联字段 → `convert_task` 增加 `result_file_id`，`ConvertTask` 增加对应字段与运行期 `outputPath`。
4. 缺少队列消费入口（仅有生产、无消费）→ 新增 `TaskPollScheduler` 打通闭环。
5. 缺少 MyBatis-Plus 分页插件（`Page` 退化为全量查询）→ 新增 `MybatisPlusConfig`。
6. `JwtAuthenticationFilter` 会被 Servlet 容器重复注册 → 增加 `FilterRegistrationBean` 关闭自动注册。
7. `RedisTaskQueue` 硬编码队列 Key，忽略配置 → 改用 `campus.queue.redis-key`。
8. `audit_log.detail` 原为 `JSON` 列，与普通文本写入不兼容 → 调整为 `VARCHAR`。
9. `StorageService` 缺少产物路径与容量实现 → 本地实现补齐 `prepareOutputPath`/容量统计；MinIO 补齐接口。
10. 前端目录缺失导致 `docker-compose` 的 `web` 服务无法构建 → 补齐 Vue 前端骨架与 Nginx 配置。
