# 学生成长档案平台 · Spring Boot 后端骨架（一期）

> 卓越工程师学院学生成长档案平台的一期后端工程，依据《一期建设总流程与可执行方案》V1.0（编码依据版）搭建，按六大档案板块划分模块，已完成工程结构、统一响应与基础配置，可编译、可启动。

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-6DB33F?logo=springboot&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-17-orange?logo=openjdk&logoColor=white)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.7-1E90FF)
![MySQL](https://img.shields.io/badge/MySQL-8.0.32%2B-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.x-DC382D?logo=redis&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9%2B-C71A36?logo=apachemaven&logoColor=white)
![Status](https://img.shields.io/badge/Status-骨架阶段-yellow)
![License](https://img.shields.io/badge/License-待确认-lightgrey)

## 项目简介

本项目是卓越工程师学院学生成长档案平台的**一期后端服务**。学院原有的学生成长档案分散在 Excel 与纸质材料中，存在口径不统一、过程不可追溯、数据不可复用三个问题；本系统把「一名学生从账号开通到档案归档」这条链路线上化，形成一份可审计、可导出、可分角色的电子档案。

系统以「板块」为流转单位而非整份档案，六个板块可分别处于不同状态，学生可以先提交已经填完的板块。

项目目标：

- **可运行**：JDK 17 + Maven 3.9 直接编译、打包、启动，不需要先建表即可跑通路由与响应结构。
- **可维护**：六大板块目录完全同构，包名与需求文档的板块编码一一对应，新增板块照抄结构即可。
- **可追溯**：所有业务规则在注释中标注文档编号（`S-1-05` / `BR-S02` / `EC-42202`），实现与需求可双向回溯。
- **边界清晰**：模块化单体，板块之间只经 Service 接口调用，禁止跨板块直连他表。

> 当前阶段**只搭后端骨架，不做前端**。所有业务方法仅保留签名与事务注解，方法体统一抛出 `EC-50000 尚未实现`，方便前端先行联调路由、状态码与错误结构。

## 接口示例

骨架阶段没有界面截图，以下为实际启动后抓取的响应，可用来确认路由与响应结构。

查询板块详情（`S-1-03`）：

```bash
curl "http://127.0.0.1:8080/api/sections/basic/1001?academicYear=2025-2026-1"
```

```json
{
  "code": "EC-50000",
  "message": "基本信息板块查询尚未实现",
  "traceId": "cdf22cb558c346d8bfc7c2f0d258d75c",
  "timestamp": "2026-11-26T13:00:19+08:00"
}
```

保存草稿时字段校验失败（`S-1-04` / `EC-42201`）：

```bash
curl -X PUT "http://127.0.0.1:8080/api/sections/basic/1001" \
  -H "Content-Type: application/json" \
  -d '{"name":"张","studentNo":"123","email":"bad"}'
```

```json
{
  "code": "EC-42201",
  "message": "字段校验未通过",
  "traceId": "f7fa2e73cb2c44d38ee157cef4ac0814",
  "timestamp": "2026-11-26T12:59:08+08:00",
  "details": [
    { "field": "studentNo", "reason": "INVALID", "message": "学号必须为 8–12 位数字" },
    { "field": "email", "reason": "INVALID", "message": "邮箱格式不正确" },
    { "field": "name", "reason": "INVALID", "message": "姓名长度为 2–32 字" }
  ]
}
```

## 已完成搭建

| 项目 | 需求文档约定 | 当前实现 |
| --- | --- | --- |
| 工程形态 | 模块化单体，按 module 分包 | 单 Maven 模块，包根 `com.college.growth`，按板块分包 |
| 六大板块 | 4.2–4.7 六个板块 | 每板块 11 个类，目录完全同构 |
| 统一响应 | 6.1 `code / message / traceId / timestamp / details` | `Result<T>` 固定结构，`details` 仅在字段级错误时出现 |
| 错误码 | `EC-xxxxx` 五位业务码，按类别分段 | `ResultCode` 枚举收口 400 / 401 / 403 / 409 / 422 / 500 常用码值 |
| 状态机 | 5.2 五状态，不得新增 | `ProfileStatusEnum` + `isWritable()`（仅 DRAFT / RETURNED 可写） |
| 技术基线 | Spring Boot 3.2.x / MyBatis-Plus 3.5.x / MySQL 8.0 | 3.2.12 / 3.5.7 / 8.0.32+ |
| 数据库迁移 | Flyway `V{n}__{描述}.sql` | 目录约定就绪，建表脚本待补 |

### 基础能力说明

- `TraceIdFilter` 为每个请求生成 32 位 traceId，写入 MDC 与响应头 `X-Trace-Id`，日志格式内置 `[%X{traceId}]`，异常响应体中的 traceId 与日志一致。
- `GlobalExceptionHandler` 统一把异常映射为「HTTP 状态码 + 业务码」：参数类 400、字段校验 422、状态冲突 409、未捕获异常 500。
- `MybatisPlusConfig` 装配分页插件与 `BlockAttackInnerInterceptor`，阻断全表 update / delete。
- `JacksonConfig` 固定东八区，`LocalDate` / `LocalDateTime` 以字符串输出，`timestamp` 为带偏移量的 ISO-8601。
- `TextUtils.normalize` 实现文档 4.1.3 的文本规范化：去首尾空格、连续空格压缩为一个。
- `docker-compose.yml` 一键起本地 MySQL 8（utf8mb4_0900_ai_ci）与 Redis 7。

## 六大板块

板块编码同时作为 `arc_profile.section_code`、字段编码前缀与接口路径段使用，全平台不出现第二套叫法。

| # | 板块 | 编码 | 包路径 | 字段前缀 | 涉及物理表 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| 一 | 基本信息 | `BASIC` | `com.college.growth.basic` | `basic.` | `arc_profile` | 身份与联系信息，含高敏字段；16 个字段中学生可直接编辑的仅 5 个 |
| 二 | 学业发展 | `ACADEMIC` | `com.college.growth.academic` | `academic.` | `arc_profile`、`arc_certificate`、`arc_event` | 学业目标、成绩轨迹、能力认定，外加证书与竞赛两张子表；证书走两段式认定 |
| 三 | 兴趣特长 | `INTEREST` | `com.college.growth.interest` | `interest.` | `arc_profile` | 兴趣爱好、技能特长、理想描述；整体提交后锁定 |
| 四 | 生活心理 | `LIFE` | `com.college.growth.life` | `life.` | `arc_profile` | 含心理与资助两类高敏信息，字段级权限最细；压力自评原始值对辅导员不可见 |
| 五 | 职业规划 | `CAREER` | `com.college.growth.career` | `career.` | `arc_profile` | 发展意向与准备情况；毕业学年允许一次整体更新（`BR-S08`） |
| 六 | 导师寄语 | `MENTOR` | `com.college.growth.mentor` | `mentor.` | `arc_mentor_note` | 无主表字段值，一行一次谈话，只追加不修改（`BR-S07`） |

板块状态固定五个：`DRAFT` → `SUBMITTED` → `APPROVED` / `RETURNED`，归档为 `LOCKED` 终态。六板块全部 `APPROVED` 时档案整体标记完成（`BR-S10`）。

## 当前可用接口

> 以下接口已注册到容器，可正常访问，但业务逻辑未实现，统一返回 `501 / EC-50000`。

| 方法 | 路径 | 说明 | 对应流程 |
| --- | --- | --- | --- |
| GET | `/api/sections/{code}/{studentId}?academicYear=` | 查询板块详情（含字段权限位） | `S-1-03` |
| PUT | `/api/sections/{code}/{studentId}` | 保存草稿，触发 `@Valid` 校验 | `S-1-04` |
| POST | `/api/sections/{code}/{studentId}/submit?academicYear=` | 提交板块 | `S-1-05` |
| GET | `/api/actuator/health` | 健康检查（actuator） | — |

`{code}` 取 `basic` / `academic` / `interest` / `life` / `career` / `mentor`。

## 尚未实现的功能

以下内容在骨架中已预留包位置或方法签名，但**尚无实现**，不计入可用范围：

- 六个板块的查询、保存、提交、审核、退回、撤回逻辑
- 字段定义驱动的表单渲染与字段级权限判定（`arc_field_def`）
- 登录鉴权、令牌、会话白名单与数据范围控制
- 建档与批量导入、单人 PDF 导出、班级 Excel 导出
- 操作审计、登录日志、变更追溯与申诉工单
- 定时任务、完成率统计、临时授权
- Flyway 建表脚本与实体字段的完整映射

## 技术栈与结构

```text
Student-Growth-PorfolioPlatform/
├── pom.xml                          # Spring Boot 3.2.12 + MyBatis-Plus 3.5.7
├── docker-compose.yml               # 本地依赖：MySQL 8 + Redis 7
├── README.md
└── src
    ├── main
    │   ├── java/com/college/growth
    │   │   ├── GrowthPlatformApplication.java     # 启动入口
    │   │   ├── common/                            # 平台公共层
    │   │   │   ├── constant/                      # 通用常量
    │   │   │   ├── enums/                         # 板块编码、板块状态
    │   │   │   ├── exception/                     # 业务异常、全局异常处理
    │   │   │   ├── result/                        # 统一响应、业务码、字段明细、分页
    │   │   │   ├── util/                          # 文本规范化
    │   │   │   └── web/                           # 链路追踪过滤器
    │   │   ├── config/                            # 全局配置：MyBatis-Plus、Jackson
    │   │   ├── basic/                             # 板块一 基本信息
    │   │   ├── academic/                          # 板块二 学业发展
    │   │   ├── interest/                          # 板块三 兴趣特长
    │   │   ├── life/                              # 板块四 生活心理
    │   │   ├── career/                            # 板块五 职业规划
    │   │   └── mentor/                            # 板块六 导师寄语
    │   └── resources/
    │       └── application.yml                    # 端口、数据源、Redis、MyBatis-Plus、日志
    └── test/java/com/college/growth/
        └── common/util/TextUtilsTest.java
```

每个板块固定 11 个类，目录结构一致：

```text
<板块>/
├── common/        <Name>Constants.java          板块编码、字段前缀、接口路径、物理表名
├── config/        <Name>ModuleConfig.java       板块专属装配点
├── controller/    <Name>SectionController.java  路由与参数入口
├── dto/           <Name>SectionSaveDTO.java     保存入参 + 接口层校验
├── entity/        <Name>Profile.java            板块实例（arc_profile）
├── exception/     <Name>SectionException.java   板块业务异常
├── mapper/        <Name>ProfileMapper.java      MyBatis-Plus 数据访问
├── service/       <Name>SectionService.java     板块对外唯一入口
├── service/impl/  <Name>SectionServiceImpl.java 业务实现（当前为占位）
├── util/          <Name>SectionUtils.java       板块内纯函数
└── vo/            <Name>SectionVO.java          板块详情视图
```

- 语言：Java 17
- 框架：Spring Boot 3.2.12（Web / Validation / Actuator / Data Redis）
- 持久层：MyBatis-Plus 3.5.7 + MySQL Connector/J
- 数据库：MySQL 8.0.32+，字符集 `utf8mb4`，排序 `utf8mb4_0900_ai_ci`
- 缓存：Redis 7.x（会话白名单、字典缓存、任务锁）
- 构建：Maven 3.9+，单模块打包，产物 `target/student-growth-platform.jar`
- 代码规模：81 个 Java 文件（80 个主代码 + 1 个测试类）

## 快速开始

### 环境要求

- JDK 17
- Maven 3.9+（IntelliJ IDEA 自带的 Maven 也可）
- MySQL 8.0.32+、Redis 7.x（或使用仓库内 `docker-compose.yml`）

### 克隆仓库

```bash
git clone https://github.com/007wwww123/Student-Growth-PorfolioPlatform.git
cd Student-Growth-PorfolioPlatform
```

### 启动本地依赖

```bash
docker compose up -d          # MySQL 3306 / Redis 6379
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS student_growth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

已有 MySQL / Redis 可跳过这一步，直接改 `application.yml` 或以环境变量覆盖连接信息。

### IntelliJ IDEA

1. `File > Open`，选择仓库根目录的 `pom.xml`，以 Maven 项目导入。
2. `Project Structure > Project SDK` 选 JDK 17，`Language level` 选 17。
3. 打开 `Settings > Build > Compiler > Annotation Processors`，勾选 **Enable annotation processing**，Lombok 才能生效。
4. 运行 `com.college.growth.GrowthPlatformApplication`。

### 命令行启动

```bash
mvn spring-boot:run
```

启动后：

- 服务地址 `http://127.0.0.1:8080/api`
- 健康检查 `http://127.0.0.1:8080/api/actuator/health`
- 冒烟验证 `curl http://127.0.0.1:8080/api/sections/mentor/1001`，预期 `501 / EC-50000`

### 打包与测试

```bash
mvn clean package                     # 编译 + 运行单元测试 + 打包
mvn test                              # 仅跑测试
java -jar target/student-growth-platform.jar
```

> 应用启动不强依赖数据库连通（连接池懒加载），但调用业务接口前需保证 MySQL 可用。`target/` 已被 `.gitignore` 忽略，不要提交。

## 配置说明

配置集中在 `src/main/resources/application.yml`，环境相关项全部通过环境变量注入，不要把真实口令写进仓库。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 服务端口，context-path 固定 `/api` |
| `SPRING_PROFILES_ACTIVE` | `dev` | 运行环境标识 |
| `DB_HOST` / `DB_PORT` | `127.0.0.1` / `3306` | MySQL 地址 |
| `DB_NAME` | `student_growth` | 库名，字符集 utf8mb4 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | 数据库账号，生产环境必须改 |
| `DB_POOL_SIZE` | `20` | Hikari 最大连接数 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | 会话白名单、字典缓存 |
| `REDIS_DATABASE` / `REDIS_PASSWORD` | `0` / 空 | Redis 库号与口令 |
| `LOG_LEVEL_APP` | `debug` | 业务包日志级别，生产建议 `info` |

- 连接串已带 `serverTimezone=Asia/Shanghai` 与 `useUnicode=true&characterEncoding=utf8`，时区与字符集不得随意改动。
- 上传限制：单文件 20MB、单请求 100MB，与文档 4.1.3 附件规则一致。

## 当前状态与已知限制

项目可以正常编译、打包、启动，六个板块的路由与统一响应已经跑通，字段校验错误能按字段返回明细。以下限制属于当前阶段的已知情况：

- 所有业务接口返回 `501 / EC-50000`，属骨架阶段的预期行为，不是缺陷。
- 没有建表脚本，`arc_profile`、`arc_profile_item`、`arc_field_def` 等表尚未创建，实体字段也未做 `@TableField` 映射。
- 未接入鉴权，任何调用方都能访问全部路由；数据范围校验（`EC-40302`）尚未实现。
- 实体与 Mapper 已按 `arc_profile` 建好骨架，六板块共用同一张表，所有查询必须显式带 `section_code` 条件。
- 前端（Vue 3 + Element Plus）尚未开始，暂无界面截图。
- 需求文档中 20 项待确认事项（`DC-xx`）未全部关闭，涉及库类型、字段口径的部分按文档默认值实现。

## Roadmap

### 板块业务（优先）

- [x] 六板块包结构、路由、DTO / VO / 实体占位
- [x] 统一响应体、业务码、全局异常处理
- [x] 板块编码与五状态枚举
- [ ] Flyway 建表脚本（`arc_profile`、`arc_profile_item`、`arc_field_def` 等）
- [ ] 字段定义驱动的表单渲染与字段级权限（`BR-F01`）
- [ ] 草稿保存与逐字段校验（`S-1-04` / `BR-F02`）
- [ ] 提交、撤回、审核、退回（`S-1-05` ~ `S-1-09`）
- [ ] 完成度计算与档案整体完成标记（`BR-S10`）

### 平台模块

- [ ] `auth` 登录、令牌、会话白名单
- [ ] `org` 学院 / 专业 / 班级
- [ ] `account` 账号 / 角色 / 数据范围
- [ ] `fieldconfig` 字段定义、字典、参数
- [ ] `archive` 建档、批量导入、变更日志
- [ ] `transfer` 单人 PDF 与班级 Excel 导出
- [ ] `audit` 操作审计与登录日志
- [ ] `job` 定时任务与统计

### 工程质量

- [x] TraceId 链路追踪与统一日志格式
- [x] MyBatis-Plus 全表更新拦截
- [x] 文本规范化单元测试
- [ ] 接口文档（OpenAPI / Swagger）
- [ ] 关键业务分支的集成测试
- [ ] CI 自动构建与静态检查
- [ ] 敏感字段脱敏的统一出入口（`BR-D05` / `BR-D07`）

## 常见问题

**启动时报数据库连接失败怎么办？**

应用启动不强依赖数据库，连接池是懒加载的；只要不调用业务接口就不会报错。`/api/actuator/health` 会显示 `db` 为 `DOWN`，属正常现象。需要跑通业务时执行 `docker compose up -d` 并确认库已创建。

**为什么所有接口都返回 501 / EC-50000？**

这是骨架阶段的预期行为。`service/impl` 中的方法体统一 `throw new BusinessException(ResultCode.NOT_IMPLEMENTED, ...)`，用于让前端先确认路由与响应结构，实现业务时替换方法体即可。

**IDEA 里 `@Data`、`@Slf4j` 报红？**

未开启注解处理。到 `Settings > Build > Compiler > Annotation Processors` 勾选 Enable annotation processing，并确认已安装 Lombok 插件、JDK 为 17。

**8080 端口被占用？**

用环境变量覆盖，例如 `SERVER_PORT=18080 mvn spring-boot:run`。

**返回的中文出现乱码？**

先确认库与表是 `utf8mb4`，再确认连接串带 `characterEncoding=utf8`。响应编码已在 `application.yml` 中强制 UTF-8，通常不需要额外改动。

**可以换成 PostgreSQL 吗？**

文档 1.4 注明改用 PostgreSQL 16 须先在 `DC-03` 确认，确认前一律按 MySQL 实现，仅类型映射与自增语法不同。

**前端代码在哪？**

本轮只做后端骨架，前端（Vue 3 + TypeScript + Vite + Element Plus）不在本次交付范围。

## 开发约定

1. **模块边界**：新板块或平台模块统一放在 `com.college.growth.<module>` 下，目录结构与现有六个板块一致；跨板块只经 Service 接口调用。
2. **后续平台模块命名**：文档 9.1 中的 `config` 业务模块（字段定义、字典、参数）落地时命名为 `fieldconfig`，避免与本仓库的 Spring 配置包 `com.college.growth.config` 冲突。
3. **字段校验**：规则以 `arc_field_def.validator` 标识存储，代码按标识注册校验器，禁止把某字段规则硬编码在接口里（`BR-F02`）；DTO 注解只做接口层兜底。
4. **错误码**：只从 `ResultCode` 取值，不新增散落字符串；新增码值须回写需求文档 6.1。
5. **状态流转**：严格按 5.2 五状态机，不得新增状态；状态变更与变更日志必须同一事务（`BR-S11`），无日志的状态变更视为缺陷。
6. **数据访问**：单表用 MyBatis-Plus，复杂查询写 `resources/mapper/<板块>/*.xml`；禁止在 Service 拼 SQL；全表 update / delete 已被拦截器阻断。
7. **敏感数据**：身份证、手机、压力自评、重大事件等按 `BR-D05` / `BR-D07` 在接口层剔除或脱敏，不依赖前端隐藏；日志禁止打印明文敏感字段。
8. **数据库迁移**：`src/main/resources/db/migration/V{n}__{描述}.sql`，编号连续不跳号，已执行脚本禁止修改；生产库禁止手工执行 DDL。
9. **Git**：`main` 可发布，`feature/*` 开发，`hotfix/*` 紧急修复，合并需 1 人评审；提交信息以 `feat:` / `fix:` / `refactor:` / `docs:` / `test:` 加中文描述。
10. **注释引用规则**：业务代码注释统一标注需求编号（如 `S-1-05`、`BR-S02`、`EC-42202`），便于回溯与测试对齐。

欢迎提交 Issue 与 Pull Request：Bug 反馈请附请求路径、请求体、traceId 与日志片段；功能建议请说明对应需求文档编号；一个 PR 尽量只解决一个问题。

## 开源协议与说明

本仓库**尚未添加 LICENSE 文件**。项目为卓越工程师学院内部建设项目，需求文档密级为「内部 · 限项目组与学院管理人员」，代码与文档未经学院书面同意不得对外分发或用于其他项目。

如需开源，须先经学院确认协议类型并补入 `LICENSE`，同时清理真实学生数据、校内地址与账号信息。

实现依据为《卓越工程师学院 · 学生成长档案平台 一期建设总流程与可执行方案》V1.0（编码依据版），涉及章节：1.2 功能清单、1.3 明确不做的事、1.4 技术基线、2.2 主流程 S-1、4.2–4.7 六大板块字段清单、5.2 档案状态机、6.1 错误码、9.1 工程约定。文档中未写明的内容视为未定义，不得由开发方自行补全；实现出现分歧时以文档规则编号为准回溯。

> 本仓库为骨架阶段的后端工程，功能与接口均以需求文档编号为准，README 中的「已完成」仅代表工程结构与基础配置，不代表业务功能可用。
