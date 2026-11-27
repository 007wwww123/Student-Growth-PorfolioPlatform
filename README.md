# 学生成长档案平台

> 把学生在校期间的六类成长记录，统一沉淀为可查、可审、可追溯的电子档案。

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-6DB33F)
![JDK](https://img.shields.io/badge/JDK-17-007396)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.7-1E90FF)
![Vue](https://img.shields.io/badge/Vue-3.5-42B883)
![Vite](https://img.shields.io/badge/Vite-5.4-646CFF)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![Redis](https://img.shields.io/badge/Redis-7-DC382D)
![Status](https://img.shields.io/badge/Status-骨架阶段-yellow)

---

## 一、项目目标

学院原有的学生成长档案分散在 Excel 与纸质材料中，口径不统一、过程不可追溯、数据不可复用。一期把「一名学生从账号开通到档案归档」这条链路线上化，范围收敛在六件事：

1. **六个板块统一建模**：基本信息、学业发展、兴趣特长、生活心理、职业规划、导师寄语，各自独立成块，可分别填写、分别提交、分别审核。
2. **字段由配置驱动**：板块字段不写死在代码和页面里，后端按字段定义表下发，前端跟着渲染，增减字段不需要改两端代码。
3. **状态可管**：草稿 → 已提交 → 已通过 → 已归档，可退回重提，每一步可追溯。
4. **响应结构统一**：所有接口返回同一种包装，业务原因由业务码表达，前端不必逐接口写异常处理。
5. **问题可定位**：每个响应带 `traceId`，与后端日志一一对应，用户报障时能直接查到那一次请求。
6. **前后端可并行**：以 `openapi.yaml` 为契约，前端先用 mock 跑页面，后端按契约补实现，互不阻塞。

> 当前处于**骨架阶段**：工程结构、接口契约、页面框架已就绪，**板块一（基本信息）已完成从建表到接口的落地**，其余五个板块仍是占位实现。进度见「六、当前进度」。

## 二、整体架构

```
[浏览器]
   │  HTTP  /api/*
   ▼
[Vue 3 单页应用  frontend/  :5173] ──VITE_USE_MOCK=true──▶ [MSW mock，浏览器内拦截]
   │  axios  baseURL=/api
   │  Vite 开发代理 → 127.0.0.1:8080（路径不重写）
   ▼
[Spring Boot 3.2  backend/  :8080] ──JDBC──▶ [MySQL 8]
   │
   └──Redis 7（缓存 / 会话，预留未接）

契约：openapi.yaml（两端都以此为准）
```

三点需要说明：

- **浏览器只与 `/api` 通信**，不直连数据库。后端 `context-path` 就是 `/api`，前端开发代理原样转发，不做路径重写。
- **`openapi.yaml` 是唯一契约**，字段名、错误码、分页、鉴权头都写在这里。契约一改，前端 mock 与后端 DTO 必须同步。
- **两端可独立运行**。前端默认走 mock，不启后端也能跑通全部页面；后端不启前端也能用 curl 验证接口。联调只是把前端的 mock 开关关掉。

## 三、仓库结构

前后端同仓，两端各占一个顶层目录：`backend/` 是独立 Maven 工程，`frontend/` 是独立 npm 工程。根目录只放**不属于任何一端**的共享文件：项目总览、接口契约、本地依赖编排。

```
Student-Growth-PorfolioPlatform/
├── backend/                 # 后端（独立 Maven 工程，构建与 IDE 导入都以这里为根）
│   ├── pom.xml              # Spring Boot 3.2.12，单模块
│   ├── src/
│   │   ├── main/java/com/college/growth/   # 六大板块 + 平台公共层
│   │   ├── main/resources/application.yml  # 端口、数据源、Redis、Jackson、日志
│   │   └── test/java/                      # 单元测试
│   └── README.md            # 后端说明：职责、分层、接口、配置、命令
├── frontend/                # 前端（独立 npm 工程）
│   ├── src/                 # 页面、路由、请求封装、mock
│   ├── package.json
│   └── README.md            # 前端说明：职责、页面、依赖、命令
├── openapi.yaml             # 接口契约（OpenAPI 3.0.3，两端共用）
├── docker-compose.yml       # 本地依赖：MySQL 8.0.32 + Redis 7（后端使用）
└── README.md                # 本文件：项目总览
```

| 位置 | 内容 | 详细说明 |
|---|---|---|
| `backend/` | 后端代码（80 个主代码类 + 1 个测试类） | [backend/README.md](backend/README.md) |
| `frontend/` | 前端代码 | [frontend/README.md](frontend/README.md) |
| `openapi.yaml` | 接口契约 | 用编辑器或 Swagger 系工具打开 |

这样划分的原因：两端各有独立的构建系统与依赖清单（Maven / npm），把后端构建文件放在根目录会让「根目录」既是项目根又是后端工程根，IDE 导入和 `mvn` 执行的位置都容易搞混，前端同学也要在根目录看到 `pom.xml`、`target/` 这类与自己无关的东西。分开后，一个人只需打开自己那一端的目录或构建文件。

> `docker-compose.yml` 与 `openapi.yaml` 留在根目录：前者是本地基础设施（不属于后端代码），后者是两端共享的契约，放进任一端都会让另一端产生「要去别人目录取文件」的依赖。

## 四、前后端关系

| 维度 | 后端 | 前端 |
|---|---|---|
| 技术栈 | Spring Boot 3.2.12 + MyBatis-Plus 3.5.7 | Vue 3.5 + Vite 5.4 + TypeScript 5.6 |
| 端口 | `8080`（`SERVER_PORT` 可改） | `5173`（Vite 默认） |
| 接口前缀 | `context-path: /api` | `baseURL: /api`，开发代理转发至 8080 |
| 数据来源 | MySQL（业务数据）+ Redis（缓存，预留） | 后端接口；后端未实现时走 MSW mock |
| 契约依据 | `openapi.yaml` | `openapi.yaml`（mock 与请求封装均按它写） |
| 统一响应 | `Result`：`code / message / traceId / timestamp / data / details` | `request<T>()` 解包，失败抛 `ApiError` |

一次请求的链路：

```
页面组件 → api/sections.ts → api/http.ts（axios，baseURL=/api）
        → Vite 代理 → Controller → Service → Mapper → MySQL
        ← Result 包装 ← ApiError 或 data ← 页面渲染
```

开发期前端 `VITE_USE_MOCK=true` 时请求在浏览器内被 MSW 拦截，不到达后端；改为 `false` 即切换真实接口，页面代码不变。

## 五、运行要求与启动方式

### 环境要求

| 依赖 | 版本 | 用途 |
|---|---|---|
| JDK | 17 | 后端编译与运行 |
| Maven | 3.9+ | 构建；IDEA 内置 Maven 亦可 |
| Node.js | 18+（实测 22.22） | 前端构建与开发服务器 |
| npm | 9+ | 随 Node 安装 |
| Docker | 可选 | 一键起 MySQL / Redis |

### 三步启动

```bash
# 1. 起本地依赖（仓库根目录）
docker compose up -d

# 2. 起后端（backend/ 下执行）
cd backend
mvn spring-boot:run
# 或 IDEA 打开 backend/pom.xml 后运行 GrowthPlatformApplication

# 3. 另开终端，起前端（frontend/ 下执行）
cd frontend
npm install && npm run dev
```

Maven 与 npm 都**不是**在仓库根目录执行：根目录没有 `pom.xml`，也没有 `package.json`。习惯在根目录敲命令的话，可以加 `-f` / `--prefix`：

```bash
mvn -f backend/pom.xml spring-boot:run
npm --prefix frontend run dev
```

| 服务 | 地址 |
|---|---|
| 前端页面 | http://localhost:5173 |
| 后端接口 | http://localhost:8080/api |
| 健康检查 | http://localhost:8080/api/actuator/health |

**只看前端**：跳过第 1、2 步，`cd frontend && npm install && npm run dev` 即可，默认 mock 不依赖后端。

**首次启动前**要按 `01 → 11` 跑完 `backend/src/main/resources/db/sql/` 下的脚本，其中 `08`/`09` 建 `sys_user` 并写入演示账号；
页面会先跳登录页，用 `2024010101` / `cou9001` / `admin0`，密码统一 `Passw0rd!`（完整清单见 [backend/README.md](backend/README.md)）。

命令参数、配置项与排错按职责分别写在两个目录的 README 里，本文不重复。

## 六、当前进度

| 能力 | 状态 |
|---|---|
| 六大板块的包结构、DTO / VO / Mapper / Entity | 已就位 |
| 板块一（基本信息）后端 | **已完成**：字段定义驱动、字段级权限与脱敏、待确认区写入/确认/驳回、提交/撤回、变更与审计双日志、乐观锁（79 条单测通过） |
| 公共审核模块（六板块共用） | **已完成**：待审列表、审核通过、审核退回、按班级判定审核权 |
| 登录与鉴权后端 | **已完成**：账号密码登录换 JWT、令牌校验过滤器、登出吊销名单、`sys_user` 与种子账号 |
| 其余五个板块的业务逻辑 | **未实现**，接口返回 `EC-50000`（HTTP 501） |
| 统一响应体、业务码、全局异常、traceId | 已完成 |
| 接口契约 `openapi.yaml` | 已完成，含 `/auth/*` 与全局 `security`，与后端代码交叉校验一致 |
| 前端六板块页面、路由、请求封装 | **已完成**：表单按后端 `fieldMeta` 渲染，支持保存 / 提交 / 撤回与待确认提示 |
| 前端审核工作台 | **已完成**：待审列表（状态页签 + 板块 + 学年筛选）、审核详情、通过 / 退回、敏感字段明文、待确认值处理 |
| 前端登录与路由守卫 | **已完成**：登录页、令牌存储、未登录跳登录、角色不足跳 403、401 统一拦截 |
| 前端 mock | 已完成，62 项断言通过（含鉴权、批量建档、批量归档、完成标记、看板与权限配置） |
| 数据库建表脚本 | **已完成板块一所需最小集**：`arc_profile`、`arc_field_def`、`arc_profile_item`、`arc_change_log`、`org_class`、`sys_audit_log`、`stu_student`、`sys_user`（+ 字段定义与种子账号） |
| 档案导出（PDF / Excel） | 未开始 |
| 批量建档（S-1-01） | **已完成**：按学年 + 班级为在读学生生成六板块 DRAFT 实例，已存在逐块跳过（幂等） |
| 前端批量建档页 | **已完成**：本班建档进度、按班级生成、结果汇总（辅导员可见） |
| 批量归档（S-1-11） | **已完成**：毕业名单整批校验六板块，全通过才转 LOCKED，任一未完成整批拒绝并返回未完成清单（EX-12） |
| 前端毕业归档页 | **已完成**：本班归档名单、六板块完成情况、批量归档确认与结果清单（辅导员可见） |
| 板块完成标记（S-1-10） | **已完成**：判定收口到 `CompletionService`，每次状态流转后同事务重算；定时任务补偿绕过状态机的旁路数据，管理端可手动重算 |
| 前端完成标记展示 | **已完成**：审核详情页展示六板块完成情况与待完成清单 |
| 首页进度聚合 | **已完成**：一次请求返回学生数、六板块完成数、待审量、六板块状态分布与班级完成率，按角色收口数据范围 |
| 前端总览看板 | **已完成**：KPI、六板块堆叠条、班级完成率（辅导员与持授权管理员可见） |
| 字段编辑权限配置（管理员模式） | **已完成**：六个板块共 51 个字段，按板块配置「仅学生 / 仅老师 / 师生均可 / 均不可」；保存写 `arc_field_def.editable_roles` 后即时生效，学生与辅导员的详情 `editable` 位、保存越权校验（`EC-40301`）同步变化；系统推导、系统生成与学号固定只读；保存与恢复默认均写 `PERMISSION_CONFIG` 审计 |
| 管理员内容写保护 | **已完成**：管理员调用板块保存/提交返回 `EC-40303`，板块页强制只读并提示「只配置权限，不改内容」 |
| 前端权限配置页 | **已完成**：六板块页签、逐字段四选一、待保存/已调整/固定只读状态、按角色统计、恢复默认、未保存提示；仅管理员菜单可见，非管理员路由拦截 |

## 七、协作约定

- **分支**：`feature/*` 开发，经 PR 合入 `main`，两人不直推 `main`；`main` 保持可发布。
- **契约变更**：改 `openapi.yaml` 单独提交并在 PR 说明，前端 mock 与后端 DTO 同步改，不允许「先改代码后补契约」。
- **提交信息**：`feat|fix|docs|chore(范围): 说明`，正文写清做了什么、怎么验证。
- **一个功能一个 PR**：同一功能的前后端改动放同一个 PR，方便对照契约检查。
- **技术约定**（模块边界、字段校验、错误码、状态机、SQL、脱敏、数据库脚本）见 [backend/README.md](backend/README.md)。
- **前端约定**（字段渲染方式、契约同步、提交前检查）见 [frontend/README.md](frontend/README.md)。

## 八、开源协议

仓库尚未添加 `LICENSE`，暂按内部项目处理：需求文档密级为「内部 · 限项目组与学院管理人员」，代码与文档未经学院书面同意不得对外分发或用于其他项目。如需开源，须先经学院确认协议类型并补入 `LICENSE`，同时清理真实学生数据与校内账号信息。

实现依据为《卓越工程师学院 · 学生成长档案平台 一期建设总流程与可执行方案》V1.0（编码依据版）：1.2 功能清单、1.3 明确不做的事、1.4 技术基线、2.2 主流程、4.2–4.7 六大板块字段清单、5.2 档案状态机、6.1 错误码、9.1 工程约定。文档未写明的内容视为未定义；实现出现分歧时按文档规则编号回溯。
