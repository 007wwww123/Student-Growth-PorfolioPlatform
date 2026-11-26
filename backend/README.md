# 学生成长档案平台 · 后端

> Spring Boot 3.2 单体服务，按六大档案板块分包，提供统一的响应结构、业务码与链路追踪。

本目录（`backend/`）是后端工程根，`pom.xml`、`src/` 都在这层，**Maven 命令均在本目录下执行**。项目总览见[根 README](../README.md)，接口契约见 [openapi.yaml](../openapi.yaml)，前端说明见 [frontend/README.md](../frontend/README.md)。

## 一、职责与边界

后端负责：

- **业务规则**：板块状态流转、字段可编辑判定、完成度计算，前端只做展示，不复制规则。
- **字段定义下发**：按字段定义表（`arc_field_def`）返回板块字段与权限位，前端据此渲染表单。
- **校验**：接口层用 Bean Validation 兜底，业务层按字段定义校验，错误按字段返回明细。
- **持久化**：MySQL 存业务数据，MyBatis-Plus 做单表访问，复杂查询走 XML。
- **统一出入口**：所有响应经 `Result` 包装，所有异常经 `GlobalExceptionHandler` 转换，每个请求带 `traceId`。
- **安全边界**：高敏字段（身份证、手机、心理与资助信息）在接口层剔除或脱敏，不依赖前端隐藏。

后端**不负责**：页面渲染、表单交互、前端路由。前后端只通过 `openapi.yaml` 约定的 HTTP 接口耦合。

## 二、技术栈

| 项 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 17 |
| 框架 | Spring Boot（Web / Validation / Actuator / Data Redis） | 3.2.12 |
| 持久层 | MyBatis-Plus（`mybatis-plus-spring-boot3-starter`） | 3.5.7 |
| 驱动 | MySQL Connector/J | 随 Boot 管理 |
| 数据库 | MySQL，字符集 `utf8mb4`，排序 `utf8mb4_0900_ai_ci` | 8.0.32+ |
| 缓存 | Redis（会话白名单、字典缓存、任务锁，预留） | 7.x |
| 构建 | Maven，单模块 | 3.9+ |
| 辅助 | Lombok、spring-boot-starter-test | — |

产物：`target/student-growth-platform.jar`。代码规模：80 个主代码类 + 1 个测试类。

## 三、目录结构

```
backend/
├── pom.xml                                   # 构建：Spring Boot 3.2.12，单模块
├── src/
│   ├── main/
│   │   ├── java/com/college/growth/
│   │   │   ├── GrowthPlatformApplication.java   # 启动入口
│   │   │   ├── common/                          # 平台公共层
│   │   │   │   ├── constant/                    # 通用常量（traceId 键名等）
│   │   │   │   ├── enums/                       # SectionCodeEnum、ProfileStatusEnum
│   │   │   │   ├── exception/                   # BusinessException、GlobalExceptionHandler
│   │   │   │   ├── result/                      # Result、ResultCode、FieldErrorDetail、PageResult
│   │   │   │   ├── util/                        # TextUtils（文本规范化）
│   │   │   │   └── web/                         # TraceIdFilter
│   │   │   ├── config/                          # JacksonConfig、MybatisPlusConfig
│   │   │   ├── basic/                           # 板块一 基本信息
│   │   │   ├── academic/                        # 板块二 学业发展
│   │   │   ├── interest/                        # 板块三 兴趣特长
│   │   │   ├── life/                            # 板块四 生活心理
│   │   │   ├── career/                          # 板块五 职业规划
│   │   │   └── mentor/                          # 板块六 导师寄语
│   │   └── resources/
│   │       └── application.yml                   # 端口、数据源、Redis、Jackson、MyBatis-Plus、日志
│   └── test/java/com/college/growth/
│       └── common/util/TextUtilsTest.java        # 文本规范化单元测试（3 条用例）
└── README.md
```

六个板块的包结构完全同构，每板块 11 个类：

| 子包 | 类 | 职责 |
|---|---|---|
| `common/` | `<Name>Constants` | 板块编码、字段前缀、接口路径、物理表名 |
| `config/` | `<Name>ModuleConfig` | 板块专属装配点（当前为空壳） |
| `controller/` | `<Name>SectionController` | 路由与参数入口，只做转发 |
| `dto/` | `<Name>SectionSaveDTO` | 保存入参 + 接口层校验注解 |
| `entity/` | `<Name>Profile` | 板块实例，对应 `arc_profile` |
| `exception/` | `<Name>SectionException` | 板块业务异常 |
| `mapper/` | `<Name>ProfileMapper` | MyBatis-Plus 数据访问 |
| `service/` | `<Name>SectionService` | 板块对外唯一入口 |
| `service/impl/` | `<Name>SectionServiceImpl` | 业务实现（当前为占位） |
| `util/` | `<Name>SectionUtils` | 板块内纯函数 |
| `vo/` | `<Name>SectionVO` | 板块详情视图 |

新增板块照抄这套结构即可，不引入第二套命名。

## 四、分层与调用链

```
HTTP 请求
  → TraceIdFilter（生成 traceId 写入 MDC 与响应头 X-Trace-Id）
  → Controller（@Valid 校验入参，DTO 收参，返回 Result<VO>）
  → Service（业务规则、状态流转、事务边界）
  → Mapper（MyBatis-Plus 单表操作，复杂查询走 XML）
  → MySQL
异常统一抛 BusinessException → GlobalExceptionHandler → Result + 对应 HTTP 状态码
```

职责边界：

- **Controller 不写业务**，不直接调 Mapper，只做参数绑定与结果包装。
- **Service 不碰 HTTP**，不感知 `Result`，返回领域对象或 VO。
- **DTO / VO / Entity 三者不混用**：DTO 只进不出，VO 只出不进，Entity 不外泄到接口层。
- **跨板块只经 Service 接口调用**，禁止跨板块直连他表。

平台公共层已具备的能力：

| 组件 | 作用 |
|---|---|
| `Result<T>` | 固定结构 `code / message / traceId / timestamp / data / details`，`data` 与 `details` 按 `non_null` 序列化 |
| `ResultCode` | 业务码枚举，含 HTTP 状态与默认文案，共 16 个码值 |
| `BusinessException` | 业务异常，携带 `ResultCode` 与自定义文案 |
| `GlobalExceptionHandler` | 参数类 400、字段校验 422、状态冲突 409、未捕获异常 500，字段校验附 `details` |
| `TraceIdFilter` | 每请求生成 32 位 traceId，写入 MDC 与 `X-Trace-Id` 响应头 |
| `TextUtils.normalize` | 去首尾空格、连续空格压缩为一个（文档 4.1.3） |
| `MybatisPlusConfig` | 分页插件 + `BlockAttackInnerInterceptor`，阻断全表 update / delete |
| `JacksonConfig` | 固定东八区，日期按字符串输出，时间为带偏移的 ISO-8601 |

## 五、六大板块

板块编码同时用作 `arc_profile.section_code`、字段编码前缀与接口路径段，全平台只有一套叫法。

| # | 板块 | 编码 | 包路径 | 字段前缀 | 涉及物理表 |
|---|---|---|---|---|---|
| 一 | 基本信息 | `BASIC` | `com.college.growth.basic` | `basic.` | `arc_profile` |
| 二 | 学业发展 | `ACADEMIC` | `com.college.growth.academic` | `academic.` | `arc_profile`、`arc_certificate`、`arc_event` |
| 三 | 兴趣特长 | `INTEREST` | `com.college.growth.interest` | `interest.` | `arc_profile` |
| 四 | 生活心理 | `LIFE` | `com.college.growth.life` | `life.` | `arc_profile` |
| 五 | 职业规划 | `CAREER` | `com.college.growth.career` | `career.` | `arc_profile` |
| 六 | 导师寄语 | `MENTOR` | `com.college.growth.mentor` | `mentor.` | `arc_mentor_note` |

六个板块共用 `arc_profile` 一张表，所有查询必须显式带 `section_code` 条件。板块状态固定五个，不得新增：

```
DRAFT（草稿） → SUBMITTED（已提交） → APPROVED（已通过） → LOCKED（已归档）
                     ↓
                 RETURNED（已退回） → 可再次提交
```

仅 `DRAFT` / `RETURNED` 允许保存与提交。

## 六、核心接口

接口按板块统一，六个板块共用三个路径模板，`{code}` 取 `basic` / `academic` / `interest` / `life` / `career` / `mentor`：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/sections/{code}/{studentId}?academicYear=` | 查询板块详情（含字段与可编辑位） |
| PUT | `/api/sections/{code}/{studentId}` | 保存草稿，触发 `@Valid` 校验 |
| POST | `/api/sections/{code}/{studentId}/submit?academicYear=` | 提交板块 |
| GET | `/api/actuator/health` | 健康检查（actuator） |

> **当前状态：三个业务接口均已注册进容器，但 Service 为占位实现，统一返回 `501 / EC-50000`。**
> 这是骨架期的预期行为，用于让前端先确认路由、状态码与响应结构。请求体、响应体、错误码的完整定义见 [openapi.yaml](../openapi.yaml)。

业务码按类别分段，明细只在契约与 `ResultCode` 里维护，此处不重复：

| 段位 | 含义 | 典型码 |
|---|---|---|
| `EC-400xx` | 参数与请求格式 | `EC-40001` / `EC-40002` / `EC-40003` |
| `EC-401xx` | 登录与令牌 | `EC-40101` / `EC-40102` |
| `EC-403xx` | 权限与数据范围 | `EC-40301` / `EC-40302` |
| `EC-409xx` | 状态冲突 | `EC-40902` / `EC-40903` |
| `EC-422xx` | 字段校验 | `EC-42201` / `EC-42202` / `EC-42203` |
| `EC-500xx` | 服务端异常 | `EC-50000` / `EC-50001` / `EC-50002` / `EC-50003` |

## 七、配置要求

配置集中在 `src/main/resources/application.yml`，环境相关项全部通过环境变量注入，**不要把真实口令写进仓库**。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | 服务端口，`context-path` 固定为 `/api` |
| `SPRING_PROFILES_ACTIVE` | `dev` | 运行环境标识 |
| `DB_HOST` / `DB_PORT` | `127.0.0.1` / `3306` | MySQL 地址 |
| `DB_NAME` | `student_growth` | 库名 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | 数据库账号，生产必须改 |
| `DB_POOL_SIZE` | `20` | Hikari 最大连接数 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 地址 |
| `REDIS_DATABASE` / `REDIS_PASSWORD` | `0` / 空 | Redis 库号与口令 |
| `LOG_LEVEL_APP` | `debug` | 业务包日志级别，生产建议 `info` |

固定项，改动前先确认影响：

- **时区与字符集**：连接串带 `serverTimezone=Asia/Shanghai`、`characterEncoding=utf8`；`server.servlet.encoding` 强制 UTF-8，不得随意调整。
- **Jackson**：`default-property-inclusion: non_null` 决定了 `data` / `details` 为空时不出现在响应里，前端按「字段可能不存在」处理。
- **MyBatis-Plus**：`id-type: assign_id`（雪花 ID）、逻辑删除字段 `deleted`（1 删除 / 0 正常）、下划线转驼峰、`mapper-locations: classpath*:/mapper/**/*.xml`（XML 目录尚未创建，写复杂查询时新建）。
- **上传限制**：单文件 20MB、单请求 100MB（文档 4.1.3 附件规则）。
- **Actuator**：只暴露 `health`、`info`，`health` 明细仅在授权时显示。

本地依赖用仓库根目录的 `docker-compose.yml`（MySQL 8.0.32 + Redis 7），与后端代码分开，不随本模块构建：

```bash
# 在 backend/ 下
docker compose -f ../docker-compose.yml up -d
# 或在仓库根目录
docker compose up -d
```

## 八、启动与测试

### IntelliJ IDEA

1. `File > Open` 选择 **`backend/pom.xml`**，以 Maven 项目导入（工程根即 `backend/`，不要再把仓库根目录当 Maven 根）。
2. `Project Structure > Project SDK` 选 JDK 17，`Language level` 选 17。
3. `Settings > Build > Compiler > Annotation Processors` 勾选 **Enable annotation processing**，否则 Lombok 不生效。
4. 运行 `com.college.growth.GrowthPlatformApplication`。

### 命令行

命令均在**本目录（`backend/`）**执行：

| 命令 | 作用 |
|---|---|
| `mvn -q compile` | 仅编译，快速验证改动 |
| `mvn spring-boot:run` | 启动服务（默认 8080） |
| `mvn test` | 运行单元测试 |
| `mvn clean package` | 编译 + 测试 + 打包 |
| `java -jar target/student-growth-platform.jar` | 运行打包产物 |

改端口：`SERVER_PORT=18080 mvn spring-boot:run`。

若习惯在仓库根目录敲命令，加 `-f` 指向 POM 即可，效果相同（产物仍生成在 `backend/target/`）：

```bash
mvn -f backend/pom.xml clean package
```

### 启动后验证

```bash
# 健康检查
curl http://127.0.0.1:8080/api/actuator/health

# 冒烟：应为 501 与 EC-50000
curl "http://127.0.0.1:8080/api/sections/basic/1001?academicYear=2025-2026-1"

# 字段校验：应为 422 与 EC-42201，details 带字段明细
curl -X PUT "http://127.0.0.1:8080/api/sections/basic/1001" \
  -H "Content-Type: application/json" \
  -d '{"name":"张","studentNo":"123","email":"bad"}'
```

## 九、扩展指引

**实现一个板块的业务**（以基本信息为例）：

1. 补建表脚本（`arc_profile` 等），给实体加 `@TableName` 与 `@TableField` 映射。
2. 在 `BasicSectionServiceImpl` 里替换占位方法体，遵守「仅 DRAFT / RETURNED 可写」。
3. 字段校验优先走定义表标识，DTO 注解只做接口层兜底。
4. 状态变更与变更日志放在同一事务。
5. 用 curl 或集成测试验证正常路径、校验失败、状态冲突三类分支。

**加字段**：先改 `SaveDTO` 与 `openapi.yaml`，再补字段定义表数据，前端 `sectionMeta.ts` 加一行即可渲染，不需要改页面结构。

**加板块**：复制任一同构板块的 11 个类改名，在 `SectionCodeEnum` 加枚举，路由自动可用；同步补契约与前端路由。

**加复杂查询**：在 `src/main/resources/mapper/<板块>/` 下建 XML，禁止在 Service 里拼 SQL。

## 十、注意事项

- **启动不依赖数据库**。连接池懒加载，MySQL 未起也能启动；此时 `/api/actuator/health` 的 `db` 为 `DOWN`，属正常现象。
- **501 不是缺陷**。六个板块的业务方法体统一抛 `BusinessException(ResultCode.NOT_IMPLEMENTED, ...)`。
- **跨域**：后端未配置 CORS，前端通过 Vite 代理走同源请求。不要用浏览器直连 `8080`，若确需跨域，另加 `CorsConfiguration` 并写进契约说明。
- **`target/` 已被忽略**，不要提交；`mvn clean` 会清掉产物。
- **端口占用**：`SERVER_PORT` 覆盖即可，不要改 `application.yml` 里的默认值。
- **中文乱码**：先确认库表是 `utf8mb4`，再看连接串是否带 `characterEncoding=utf8`。
- **不要换库**：改用 PostgreSQL 需先在需求文档 `DC-03` 确认，确认前一律按 MySQL 实现。
- **无鉴权**：当前任何调用方都能访问全部路由，数据范围校验（`EC-40302`）尚未实现，不要据此上线。

## 十一、技术约定

1. **模块边界**：新模块放 `com.college.growth.<module>`，结构对齐现有板块；跨板块只经 Service 接口。
2. **命名避让**：文档 9.1 的 `config` 业务模块落地时命名 `fieldconfig`，避免与 Spring 配置包 `com.college.growth.config` 冲突。
3. **字段校验**：规则以 `arc_field_def.validator` 标识存储，代码按标识注册校验器，禁止把某字段规则硬编码在接口里。
4. **错误码**：只从 `ResultCode` 取，不新增散落字符串；新增码值须同步 `openapi.yaml` 与需求文档 6.1。
5. **状态流转**：严格按五状态机，不新增状态；状态变更与变更日志必须同一事务。
6. **数据访问**：单表用 MyBatis-Plus，复杂查询写 XML；禁止在 Service 拼 SQL。
7. **敏感数据**：身份证、手机、压力自评、重大事件按文档 `BR-D05` / `BR-D07` 在接口层剔除或脱敏；日志禁止打印明文敏感字段。
8. **数据库迁移**：`src/main/resources/db/migration/V{n}__{描述}.sql`，编号连续不跳号，已执行脚本禁止修改。
9. **注释引用**：业务代码注释标注需求编号（如 `S-1-05`、`BR-S02`、`EC-42202`），便于回溯与测试对齐。
