# 学生成长档案平台 · 后端

> Spring Boot 3.2 单体服务，按六大档案板块分包，提供统一的响应结构、业务码与链路追踪。

本目录（`backend/`）是后端工程根，`pom.xml`、`src/` 都在这层，**Maven 命令均在本目录下执行**。项目总览见[根 README](../README.md)，接口契约见 [openapi.yaml](../openapi.yaml)，前端说明见 [frontend/README.md](../frontend/README.md)。

## 一、职责与边界

后端负责：

- **业务规则**：板块状态流转、字段可编辑判定、完成度计算，前端只做展示，不复制规则。
- **字段定义下发**：按字段定义表（`arc_field_def`）返回板块字段与权限位，前端据此渲染表单。
- **校验**：接口层校验学年与正文结构，业务层按字段定义校验，错误按字段返回明细。
- **持久化**：MySQL 存业务数据，业务读写使用参数化 JDBC，保留既有 MyBatis-Plus 配置。
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

产物：`target/student-growth-platform.jar`。

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
│   │   │   ├── mentor/                          # 板块六 导师寄语
│   │   │   ├── identity/                        # 会话与权限
│   │   │   ├── profile/                         # 共享板块存储
│   │   │   └── workflow/                        # 退回、审核及归档
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
| `dto/` | `<Name>SectionSaveDTO` | 保存入参 + 现有字段声明及局部变更映射 |
| `entity/` | `<Name>Profile` | 板块实例，对应 `arc_profile` |
| `exception/` | `<Name>SectionException` | 板块业务异常 |
| `mapper/` | `<Name>ProfileMapper` | MyBatis-Plus 数据访问 |
| `service/` | `<Name>SectionService` | 板块对外唯一入口 |
| `service/impl/` | `<Name>SectionServiceImpl` | 委托共享 ProfileStore 完成查询、保存与提交 |
| `util/` | `<Name>SectionUtils` | 板块内纯函数 |
| `vo/` | `<Name>SectionVO` | 板块详情视图 |

新增板块照抄这套结构即可，不引入第二套命名。

## 四、分层与调用链

```
HTTP 请求
  → TraceIdFilter（生成 traceId 写入 MDC 与响应头 X-Trace-Id）
  → Controller（绑定请求字段与学年，返回 Result<VO>）
  → Service（业务规则、状态流转、事务边界）
  → ProfileRepository（通过参数化 JDBC 访问业务表）
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
| 二 | 学业发展 | `ACADEMIC` | `com.college.growth.academic` | `academic.` | `arc_profile` |
| 三 | 兴趣特长 | `INTEREST` | `com.college.growth.interest` | `interest.` | `arc_profile` |
| 四 | 生活心理 | `LIFE` | `com.college.growth.life` | `life.` | `arc_profile` |
| 五 | 职业规划 | `CAREER` | `com.college.growth.career` | `career.` | `arc_profile` |
| 六 | 导师寄语 | `MENTOR` | `com.college.growth.mentor` | `mentor.` | `arc_profile` |

六个板块共用 `arc_profile` 一张表，所有查询必须显式带 `section_code` 条件。板块状态固定五个，不得新增：

```
DRAFT（草稿） → SUBMITTED（已提交） → APPROVED（已通过） → LOCKED（已归档）
                     ↓
                 RETURNED（已退回） → 可再次提交
```

仅 `DRAFT` / `RETURNED` 允许保存与提交。

## 六、核心接口

接口按板块统一，六个板块共用三个操作，`{code}` 取 `basic` / `academic` / `interest` / `life` / `career` / `mentor`：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/sections/{code}/{studentId}?academicYear=` | 查询板块详情（含字段与可编辑位） |
| PUT | `/api/sections/{code}/{studentId}` | 局部保存草稿，校验字段定义和授权 |
| POST | `/api/sections/{code}/{studentId}/submit?academicYear=` | 提交板块 |
| GET / POST | `/api/auth/me`、`/api/auth/logout` | 当前身份、撤销会话 |
| GET | `/api/workflow/{studentId}?academicYear=` | 已建档板块及其状态、版本 |
| POST | `/api/workflow/{studentId}/{code}/{action}?academicYear=` | approve、return、withdraw 携带 If-Match |
| POST | `/api/workflow/{studentId}/archive?academicYear=` | 归档六个已通过的板块 |
| GET | `/api/actuator/health` | 健康检查（actuator） |

业务接口使用 Bearer 令牌。客户端根据详情返回的 version 设置 If-Match，保存或提交成功后重新查询。审核操作见 /api/workflow，整份归档使用六个板块的版本映射。请求和响应格式见 [openapi.yaml](../openapi.yaml)。

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

# 将预签发令牌放入本地 TOKEN 环境变量后验证自己的身份与授权
curl -H "Authorization: Bearer ${TOKEN}" http://127.0.0.1:8080/api/auth/me

# 使用已授权的学生与学年查询详情，获取版本与可编辑字段
curl -H "Authorization: Bearer ${TOKEN}" \
  "http://127.0.0.1:8080/api/sections/basic/1001?academicYear=2025-2026-1"
```

## 九、扩展指引

**调整板块字段**：在新的 SQL 迁移中更新字段定义，确认类型、可见性、可写角色和校验规则。真实前端按 fieldDefinitions 渲染，开发用 mock 须单独同步。更新 SQL 初始化文件列表及契约示例，并用授权会话验证字段读写与错误返回。

**加板块**：复制任一同构板块的 11 个类改名，在 `SectionCodeEnum` 加枚举，路由自动可用；同步补契约与前端路由。

**加复杂查询**：在 `src/main/resources/mapper/<板块>/` 下建 XML，禁止在 Service 里拼 SQL。

## 十、注意事项

- **数据库启动顺序**：先启动 MySQL，后端按 application.yml 中的 schema-locations 执行幂等初始化脚本，需要数据库连接与建表权限。
- **跨域**：后端未配置 CORS，前端通过 Vite 代理走同源请求。不要用浏览器直连 `8080`，若确需跨域，另加 `CorsConfiguration` 并写进契约说明。
- **`target/` 已被忽略**，不要提交；`mvn clean` 会清掉产物。
- **端口占用**：`SERVER_PORT` 覆盖即可，不要改 `application.yml` 里的默认值。
- **中文乱码**：先确认库表是 `utf8mb4`，再看连接串是否带 `characterEncoding=utf8`。
- **不要换库**：改用 PostgreSQL 需先在需求文档 `DC-03` 确认，确认前一律按 MySQL 实现。
- **业务鉴权**：业务请求需提供未过期的 Bearer 令牌，角色和学生范围由服务端核验。会话开通见下文。

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

## 预签发身份会话

业务端点使用 Bearer 令牌。实际授权来源由负责账号开通的人员核对；当前仓库提供数据库预签发方式。将数据库迁移应用到本地 MySQL 后，通过已配置凭据的 mysql 客户端运行以下示例。参数中的身份和授权范围须替换为核实的值。

```sh
python3 scripts/issue-session.py --database student_growth --role STUDENT --actor 1001 --students 1001 --years 2025-2026-1 --hours 1
```

数据库名以实际配置为准。签发器仅在写入成功后输出一次令牌，数据库保存 SHA-256 摘要。通过受保护的渠道将令牌交给对应使用者，线上请求使用 HTTPS。客户端用 `GET /api/auth/me` 核对当前角色、学生和学年范围，`POST /api/auth/logout` 撤销当前会话。每次业务请求都会重新核验数据库授权和到期时间。数据库角色可以删除对应摘要立即撤销，人员授权变更时应同时撤销其旧会话。`studentIds` 与 `academicYears` 代表该会话获准的学生和学年的所有组合；不同学年有不同名单时分别签发。学生身份 ID 与本人档案 ID 对应。

### 真实数据库回归

在专用的空 MySQL 测试库中，配置 DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD，从 backend 目录执行 `GROWTH_MYSQL_TEST=true mvn package`。测试自动执行建表脚本，覆盖数据范围、动态字段、事务回滚、并发审核、撤回次数与整体归档。常规的 `mvn test` 会跳过需要真实数据库的测试。
