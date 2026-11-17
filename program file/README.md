# 校园课程资源格式适配与转换服务系统（CampusFormat）

面向校园课程资源的格式适配与转换服务平台：用户上传文档、音频、图片、视频、字幕等资源，系统按格式模板排队调度，调用开源引擎完成转换，经质检、登记、通知后供下载，并形成"上传 → 校验 → 排队 → 转换 → 质检 → 下载 → 反馈 → 清理"的完整闭环。

支持学生、教师/助教、课程管理员、运维、系统管理员五类角色，用户端与管理端共用一套 API 与服务端能力。

## 项目亮点

- **10 类转换引擎，SPI 可插拔**：文档、音频、图片、视频、字幕、NCM、CAJ、DjVu、Pages、公式/OCR；新引擎只需实现 `ConversionEngine` 并注册。
- **能力目录驱动**：`GET /formats` 返回全部源/目标格式、参数 Schema 与引擎可用性，可选引擎缺失时自动标记 `available=false`，不影响核心转换。
- **安全的任务状态机**：12 个状态、CAS 乐观流转、Redis ZSet 优先级队列、配额原子扣减、失败重试上限。
- **五角色 RBAC + 审计 + 配额**：JWT + BCrypt，任务归属校验，操作审计脱敏。
- **完整反馈闭环**：用户提交/撤回/查看反馈，管理端统计、筛选、回复、状态流转。
- **双端界面**：用户端（上传/任务/历史/反馈/AI）与管理端（用户/模板/统计/反馈管理/运维）。
- **一键部署**：Docker Compose 编排 MySQL、Redis、后端（内置 ffmpeg/ImageMagick/LibreOffice/CJK 字体）与前端 Nginx。

## 技术栈

| 层次 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite 5 + Vue Router 4 + Pinia 2 + Element Plus 2 + Axios |
| 后端 | Spring Boot 3.2.5（Java 17）+ Spring Security + JWT + MyBatis-Plus 3.5.5 |
| 数据库 | MySQL 8 |
| 缓存与队列 | Redis 7（任务队列、配额计数、下载令牌一次性校验、限流） |
| 存储 | 本地文件系统（`StorageService` SPI，MinIO 接入点预留） |
| 转换引擎 | LibreOffice、ffmpeg、ImageMagick、caj2pdf、DjVuLibre、Tesseract、pix2tex 等，通过 `ConversionEngine` SPI 接入 |
| AI 增强 | 规则引擎兜底（默认、离线），可选 OpenAI 兼容 LLM，全部可降级 |
| 部署 | Docker + Docker Compose + Nginx |

## 目录结构

```
program file/
├── deploy/                       # 部署：docker-compose、数据库 DDL、环境变量模板
│   ├── docker-compose.yml
│   ├── schema.sql                # 全量建库脚本（新库）
│   ├── schema-v2-multiformat-feedback.sql  # 多格式 + 反馈模块增量迁移
│   └── .env.example
├── server/                       # Spring Boot 后端
│   └── src/main/java/com/campus/convert/
│       ├── ai/                   # AI 增强（rule 默认 / llm 可选）
│       ├── common/               # Result、BizException、BaseContext、ResultCode
│       ├── config/               # Security、MyBatis-Plus、异步、异常、Knife4j、CORS
│       ├── controller/           # auth/file/task/format/feedback/notification/ai/admin/ops
│       ├── domain/               # entity、enums、dto
│       ├── engine/               # 引擎 SPI + Registry + ProcessRunner
│       │   ├── impl/             # 10 类引擎实现
│       │   ├── subtitle/         # SRT/VTT/ASS 解析与序列化（纯 Java）
│       │   ├── ncm/              # NCM 解密（纯 Java）
│       │   └── qc/               # 规则/AI 质检
│       ├── mapper/               # MyBatis-Plus Mapper
│       ├── queue/                # Redis ZSet 队列 + TaskDispatcher
│       ├── scheduler/            # 队列轮询、过期清理、配额重置、存储监控
│       ├── security/             # JWT、限流、下载令牌一次性校验
│       ├── service/              # 业务接口 + impl
│       ├── storage/              # 存储 SPI（local / MinIO 预留）
│       └── util/                 # 文件哈希、魔数识别
└── web/                          # Vue 3 前端
    └── src/
        ├── api/                  # Axios 封装与接口模块
        ├── components/           # 上传器、格式选择器、参数表单、状态徽章
        ├── router/               # 路由与角色守卫
        ├── stores/               # Pinia（user/task）
        ├── utils/                # 常量与格式化
        └── views/                # 登录/上传/任务/历史/反馈/AI/管理端/运维端
```

## 业务闭环

```
登录 → 上传文件 → 魔数校验/大小/配额 → 选择目标格式与参数 → 创建转换任务
     → 任务入队（Redis ZSet） → TaskPollScheduler 轮询 → TaskDispatcher 取出
     → EngineRegistry 路由引擎执行 → QcChecker 质检 → 登记产物 FileRecord
     → 通知 + 用户下载（登录态或一次性下载令牌） → 反馈评分/管理回复
     → 定时过期清理 → 管理端统计与审计
```

关键接线点：

- `scheduler/TaskPollScheduler` 定时消费 `queue/TaskQueue`；
- `queue/TaskDispatcher` 驱动 `CONVERTING → QC → SUCCESS/FAILED` 状态机；
- `engine/EngineRegistry` 依据 `format_template` 解析模板并分配产物路径；
- `service/impl/TaskServiceImpl` 实现 `TaskStatusEnum` 定义的全部合法流转。

## 支持的格式

| 源格式 | 目标格式 | 引擎 | 外部依赖 | 说明 |
|---|---|---|---|---|
| doc/docx/ppt/pptx | pdf | LIBREOFFICE | LibreOffice | 支持 CJK 字体 |
| flac/wav/m4a | mp3 | FFMPEG | ffmpeg | 码率 128k–320k |
| png/jpg/jpeg | webp | IMAGEMAGICK | ImageMagick | quality 10–100 |
| m4s/mov/hevc/mkv/avi | mp4 | FFMPEG | ffmpeg | 优先 `-c copy`，失败回退 H.264/AAC |
| ncm | mp3/flac | NCM | 无（纯 Java 解密） | 解密后同格式直出，否则 ffmpeg 转码 |
| srt/vtt/ass/ssa | srt/vtt/ass | SUBTITLE | 无（纯 Java） | 解析 → 统一 Cue → 序列化 |
| heic/heif | jpg/png/webp | IMAGEMAGICK | ImageMagick + libheif | 需 HEIC delegate |
| pages | pdf | PAGES | 外部转换器 | 需配置 `campus.engine.pages-command` |
| caj | pdf | CAJ | caj2pdf | 可选引擎 |
| djvu/djv | pdf | DJVU | DjVuLibre（`ddjvu`） | 可选引擎 |
| png/jpg/jpeg/heic | txt/latex | FORMULA_OCR | Tesseract / pix2tex | `txt` 走 Tesseract，`latex` 走 pix2tex；可选引擎 |

> CAJ / DjVu / Pages / OCR 为可选引擎：未安装时 `/formats` 中 `available=false`，不影响其他转换。

## 快速开始

### 方式一：Docker Compose（推荐）

```bash
cd deploy
cp .env.example .env          # Windows: copy .env.example .env
# 修改 .env 中的 MYSQL_ROOT_PASSWORD、CAMPUS_JWT_SECRET
docker compose up -d --build
```

启动后：

- 前端：<http://localhost>
- 后端 API：<http://localhost:8080/api>
- 接口文档：<http://localhost:8080/api/doc.html>

首次访问请先注册；**系统第一个注册用户自动成为 `SYS_ADMIN`**，可用其配置角色、模板与配额。

> MySQL 与 Redis 仅在同一 Docker 网络内互通，不映射宿主机端口（避免 Windows 保留端口冲突）；如需调试，可临时在 `docker-compose.yml` 中增加端口映射。

### 方式二：本地开发

前置条件：JDK 17、Maven 3.9+、Node 18+、MySQL 8、Redis 7；如需真实转换，安装 ffmpeg / LibreOffice / ImageMagick（Windows 下 ImageMagick 使用 `magick`）。

```bash
# 1. 初始化数据库（新库）
mysql -u root -p campus_convert < deploy/schema.sql
# 已有库：执行增量迁移
mysql -u root -p campus_convert < deploy/schema-v2-multiformat-feedback.sql

# Windows PowerShell 不支持 `<` 输入重定向，可改用：
# Get-Content deploy/schema.sql | mysql -u root -p campus_convert

# 2. 后端（默认 dev profile，连接 localhost:3306 / 6379）
cd server
mvn spring-boot:run

# 3. 前端
cd web
npm install
npm run dev      # http://localhost:5173，/api 代理到 8080
```

引擎路径可在 `application.yml` 或环境变量中配置；Windows 示例：

```powershell
$env:CAMPUS_ENGINE_FFMPEG_PATH="C:\tools\ffmpeg\bin\ffmpeg.exe"
$env:CAMPUS_ENGINE_LIBREOFFICE_PATH="C:\Program Files\LibreOffice\program\soffice.exe"
$env:CAMPUS_ENGINE_IMAGEMAGICK_PATH="C:\Program Files\ImageMagick-7.1.2\magick.exe"
```

## 主要配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `campus.jwt.secret` | 环境变量 | JWT 签名密钥，生产必须替换 |
| `campus.storage.type` | `local` | 存储实现（`local` / `minio` 预留） |
| `campus.storage.download-token-ttl-minutes` | `10` | 下载令牌有效期（一次性使用） |
| `campus.engine.*-path` | `ffmpeg` 等 | 各转换引擎可执行文件路径 |
| `campus.engine.poll-interval-ms` | `2000` | 任务队列轮询间隔 |
| `campus.engine.qc.provider` | `rule` | 质检器：`rule` / `ai` |
| `campus.ai.provider` | `rule` | AI：`rule`（离线）/ `llm` |
| `campus.file.allowed-extensions` | 见 yml | 允许上传的源格式白名单 |
| `campus.file.expire-days` | `7` | 文件/任务过期天数 |
| `campus.task.max-retry` | `3` | 任务最大重试次数 |
| `campus.rate-limit.*` | 见 yml | 登录/注册/上传/建任务限流阈值（每分钟） |

## 测试与构建

```bash
# 后端
cd server
mvn test                 # 单元测试
mvn -DskipTests package  # 生成 target/campus-format-server.jar

# 前端
cd web
npm run build            # 生成 dist/

# 端到端（需 Compose 已启动）
cd ../deploy
python smoke-test.py --base-url http://localhost:8080/api --check-rate-limit
python extended-smoke.py
python stability-test.py
```

当前 **21 项**单元测试，覆盖任务状态机、引擎支持判定、JSON 参数解析、文件魔数识别、字幕互转、NCM 解密、JWT 下载令牌。

2026-11-17 在 Docker Compose 环境实测：基础链路 11/11、扩展引擎 8/8、并发与负向 7/7，详见 `端到端冒烟记录.md`。

## 角色与权限

| 角色 | 能力 |
|---|---|
| `STUDENT` | 上传/转换/下载、任务与历史、反馈、AI 助手 |
| `TEACHER` | 学生能力 + 教学场景使用 |
| `COURSE_ADMIN` | 管理端：用户/模板/统计/反馈管理 |
| `OPS` | 运维端：引擎健康、队列、存储、反馈管理 |
| `SYS_ADMIN` | 全部权限 |

## 反馈模块

- 用户端：`/api/feedback` 提交（任务反馈/通用反馈、匿名、评分、分类）、我的反馈、详情、撤回。
- 管理端：`/api/admin/feedback` 列表、统计、详情、回复、状态修改。
- 状态流转：`PENDING → PROCESSING → RESOLVED → CLOSED`；用户可撤回待处理反馈，重新提交任务反馈会回到 `PENDING`。

## 已知边界与后续规划

- **Docker Compose 端到端已实测通过**（基础 11/11、扩展 8/8、并发 7/7，见 `端到端冒烟记录.md`）。
- MinIO 存储仍为接入点（`MinioStorageServiceImpl` 占位），当前使用本地存储。
- 尚未引入 Flyway/Liquibase，数据库变更通过 `schema*.sql` 手工执行。
- Testcontainers 集成测试、前端组件测试、CI/CD 尚未落地（已有三组可复现的端到端脚本）。
- 实时进度当前为前端轮询，后续可升级 SSE/WebSocket。
- 多端（Windows/iOS/HarmonyOS/Kylin）与多 AI 提供方 SPI 为后续演进方向。

## 文档索引

- `项目人工周期/2026.10.26（第2~5周）/2026.10.27项目中期分析.md`：中期审计与修复记录
- `项目人工周期/2026.10.26（第2~5周）/后续项目工作流设计.md`：六阶段演进路线
- `项目人工周期/2026.10.26（第2~5周）/多格式转换与反馈模块API设计.md`：多格式与反馈接口契约
- `项目人工周期/2026.10.26（第2~5周）/端到端冒烟记录.md`：三组冒烟脚本与实测结果
- `项目人工周期/2026.10.26（第2~5周）/项目完整性-稳定性-可用性报告.md`：结项三维度评估报告
- `项目人工周期/2026.10.26（第2~5周）/结项验收清单.md`：P0/P1 完成度对照
- `项目人工周期/2026.10.26（第2~5周）/调研数据模板.md`：问卷采集与统计模板
- `项目人工周期/2026.10.26（第2~5周）/演示脚本.md`：5 分钟现场演示流程与预案
- `项目人工周期/2026.10.26（第2~5周）/PPT逐页讲稿.md`：17 页讲稿与 Q&A 预案
- `项目人工周期/2026.10.26（第2~5周）/校园课程资源格式适配与转换服务系统-结项汇报.pptx`：结项答辩 PPT
- `program file/deploy/smoke-test.py`：基础端到端冒烟脚本（Python 标准库）
- `program file/deploy/extended-smoke.py`：能力目录与图片/文档/字幕引擎验证
- `program file/deploy/stability-test.py`：并发与负向测试
- `program file/deploy/demo-sample.srt`：现场演示样例文件
