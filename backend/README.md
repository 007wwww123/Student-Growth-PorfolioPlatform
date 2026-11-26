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
│   │   │   ├── common/                          # 平台公共层（六板块复用）
│   │   │   │   ├── constant/                    # 通用常量（traceId 键名、板块数量）
│   │   │   │   ├── context/                     # CurrentUser、UserContext（骨架期请求头身份）
│   │   │   │   ├── enums/                       # SectionCodeEnum、ProfileStatusEnum、UserRoleEnum
│   │   │   │   ├── exception/                   # BusinessException、GlobalExceptionHandler
│   │   │   │   ├── field/                       # 字段定义、校验器注册表、脱敏规则
│   │   │   │   ├── log/                         # 变更日志（arc_change_log）与审计日志（sys_audit_log）
│   │   │   │   ├── org/                         # 班级实体、学生班级与审核权解析
│   │   │   │   ├── profile/                     # 板块实例/字段值、值编解码、待确认区服务
│   │   │   │   ├── result/                      # Result、ResultCode、FieldErrorDetail、PageResult
│   │   │   │   ├── util/                        # 文本规范化、学年计算
│   │   │   │   └── web/                         # TraceIdFilter、UserContextFilter
│   │   │   ├── config/                          # JacksonConfig、MybatisPlusConfig
│   │   │   ├── review/                          # 公共审核模块（六板块共用，见第六节）
│   │   │   ├── basic/                           # 板块一 基本信息
│   │   │   ├── academic/                        # 板块二 学业发展
│   │   │   ├── interest/                        # 板块三 兴趣特长
│   │   │   ├── life/                            # 板块四 生活心理
│   │   │   ├── career/                          # 板块五 职业规划
│   │   │   └── mentor/                          # 板块六 导师寄语
│   │   └── resources/
│   │       ├── application.yml                   # 端口、数据源、Redis、Jackson、MyBatis-Plus、日志
│   │       ├── db/sql/                           # 建表与种子脚本，按 01 → 04 顺序执行
│   │       └── mapper/review/                    # 待审列表 SQL（复杂查询走 XML）
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

仅 `DRAFT` / `RETURNED` 允许保存与提交。状态流转只能调用公共审核模块
（`com.college.growth.review`），板块实现不得直接改 `arc_profile.status`。

板块一已落地的支撑表（其余板块复用，只是 `section_code` 不同）：

| 表 | 作用 | 板块一的量 |
|---|---|---|
| `arc_field_def` | 字段定义：类型、必填、来源、可见/可编辑角色、校验标识、枚举 | 16 条 |
| `arc_profile_item` | 字段值，一行一个字段；`pending_*` 为待辅导员确认区（BR-F06） | 按需生成 |
| `arc_change_log` | 字段修改与状态流转留痕，与业务同事务（BR-S11） | 按操作生成 |
| `sys_audit_log` | 审计日志：提交、撤回、审核、查看明文、处理待确认值，不存业务明文 | 按操作生成 |

字段的类型、校验、必填、权限全部读定义表，代码里不再维护第二份字段清单（BR-F02）。
新增一个字段＝加一条 `arc_field_def`，不需要改 Service、DTO 或接口契约。

## 六、核心接口

板块级接口按统一路径暴露，`{code}` 取 `basic` / `academic` / `interest` / `life` / `career` / `mentor`：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/sections/{code}/{studentId}?academicYear=` | 查询板块详情（含 `fieldMeta` 字段权限位） |
| PUT | `/api/sections/{code}/{studentId}` | 保存草稿，逐字段校验后写入 |
| POST | `/api/sections/{code}/{studentId}/submit?academicYear=` | 提交板块，校验必填后交状态机转 `SUBMITTED` |
| POST | `/api/sections/{code}/{studentId}/withdraw?academicYear=` | 撤回提交（仅学生本人，限一次） |
| GET | `/api/sections/{code}/{studentId}/fields/{fieldCode}/reveal` | 查看敏感字段明文（仅 COU/临时授权管理员，写审计） |
| POST | `/api/sections/{code}/{studentId}/fields/{fieldCode}/confirm` | 确认待确认值（仅本班辅导员） |
| POST | `/api/sections/{code}/{studentId}/fields/{fieldCode}/reject` | 驳回待确认值（仅本班辅导员） |
| GET | `/api/actuator/health` | 健康检查（actuator） |

审核是跨板块能力，单独一组路径（六个板块共用，不复制到各板块）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/review/tasks?sectionCode=&status=&classId=&academicYear=&pageNum=&pageSize=` | 待审列表，辅导员只看本班 |
| POST | `/api/review/{code}/{studentId}/approve` | 审核通过，`SUBMITTED → APPROVED` |
| POST | `/api/review/{code}/{studentId}/reject` | 审核退回，理由 ≥10 字，`SUBMITTED → RETURNED` |

> **当前状态：板块一（basic）后端闭环（7 个板块级接口）；审核模块 3 个接口可用**；
> 其余五个板块 Service 仍为占位，返回 `501 / EC-50000`。
> 请求体、响应体、错误码的完整定义见 [openapi.yaml](../openapi.yaml)。

### 公共审核模块（`com.college.growth.review`）

| 类 | 职责 |
|---|---|
| `ProfileWorkflowService` | 唯一状态机：提交、撤回、通过、退回，含角色与状态校验、双日志、完成标记重算 |
| `ReviewTaskService` | 待审列表：跨板块一条 SQL，按班级/状态/学年筛选，数据范围按辅导员限制 |
| `ReviewDecisionService` | 审核结论：定位板块实例后交状态机执行，板块差异只体现为 `sectionCode` |
| `ReviewController` | `/api/review/**` 三个接口 |

新增板块时**不需要**写审核代码：只要板块的提交动作调用 `ProfileWorkflowService.submit(...)`，
就能被审核模块纳管。板块负责「内容是否齐全」，审核模块负责「谁能改、何时能改、留什么痕」。

骨架期没有接入统一认证，当前用户由请求头模拟（**只用于本地联调，不是鉴权**）：

| 请求头 | 取值 | 说明 |
|---|---|---|
| `X-User-Id` | 数字，缺省 0 | 操作人 ID，写入变更日志 |
| `X-User-Role` | `STU` / `COU` / `ADMIN`，缺省 `STU` | 决定字段可见与可编辑；`ADMIN` 默认看不到业务字段 |
| `X-Temp-Grant` | `true` / `false` | 管理员临时只读授权（审批通过后最长 4 小时） |

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

### 数据库初始化（首次启动前）

建库脚本在 `src/main/resources/db/sql/`，七个文件按文件名前缀数字 **01 → 07** 顺序执行；脚本幂等，可重复跑：

```bash
# 01 建表：板块实例、字段定义、字段值、变更日志、班级
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/01_create_core_tables.sql
# 02 班级种子（basic.classId 的引用对象，同时决定专业推导）
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/02_insert_class_seed.sql
# 03 板块一 16 个字段定义
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/03_insert_basic_field_defs.sql
# 04 系统审计日志表（状态流转、审核、查看明文、待确认处理都要写）
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/04_create_audit_log.sql
# 05 升级：arc_field_def 增加 source_field 并回填 7 个 SIS 字段映射
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/05_alter_field_def_source_field.sql
# 06 学生主数据表 stu_student（SIS 落地副本）
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/06_create_student_table.sql
# 07 学生种子（4 人，覆盖三个班级，身份证号均通过校验位）
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/07_insert_student_seed.sql

# 自检：应为 total=16、required_cnt=8、sis_mapped=7、students=4
mysql -uroot -proot --default-character-set=utf8mb4 student_growth -e \
  "select count(*) total, sum(required=1) required_cnt, sum(source_field is not null) sis_mapped from arc_field_def where section_code='BASIC';
   select count(*) students from stu_student;"
```

> **存量库升级**：老库没有 `arc_field_def.source_field` 列，先跑 `05`（先 ALTER 再回填，可重复执行），
> 再跑 `06`/`07`。`03` 不涉及这一列，无需重跑；`01` 只对全新库生效（`CREATE TABLE IF NOT EXISTS`）。

> 学生主数据的值与档案的关系：`stu_student` 是权威副本，`StudentSyncService` 在**建档、保存、提交**时
> 把 SIS 字段物化进 `arc_profile_item`（只补空值，不覆盖已有值）。学工系统改正数据后用
> `POST /api/sections/basic/{studentId}/sync-student` 强制覆盖，仅本班辅导员或持授权管理员可调用。

> **必须带 `--default-character-set=utf8mb4`**：中文 Windows 的 mysql 客户端默认 `gbk`，而脚本是 UTF-8，
> 不指定时中文 COMMENT 转换失败会导致整条 `CREATE TABLE` 被跳过（现象是"执行成功但表没建出来"）。

> Windows 下在 Git Bash 或 IDEA 的 Database 控制台执行；若库尚未创建，先 `CREATE DATABASE student_growth DEFAULT CHARSET utf8mb4;`。
> 暂未引入 Flyway：脚本是普通 SQL，按 `01 → 07` 顺序手工执行。将来接入 Flyway 时，改名为 `V1__`、`V2__` 前缀并移入 `db/migration` 即可纳管。

**已知版本差异**

| 项 | 说明 |
|---|---|
| `SENSITIVE` 保留字 | 自 MySQL 9.0 起 `SENSITIVE` 是保留字（8.0 不是）。字段定义的敏感级别列已改名 `sensitive_level`，**不要改回 `sensitive`**，否则建表与 MyBatis-Plus 生成的 SQL 都会报 1064。 |
| `VALUES()` 弃用 | 种子里 `ON DUPLICATE KEY UPDATE col = VALUES(col)` 在 MySQL 8.0.20+ 会告警（未来版本将移除），当前不影响执行；升级到移除该语法的版本前需改为 `AS new` 别名写法。 |
| 实测版本 | 已在 MySQL 9.7.0 上跑通全部脚本与接口；`docker-compose.yml` 指定的是 8.0.32，两者均兼容。 |

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
# 0) 健康检查
curl http://127.0.0.1:8080/api/actuator/health

# 1) 详情：学生视角（缺省角色即 STU），未建档时返回 DRAFT + 16 条 fieldMeta，fields 为空
curl "http://127.0.0.1:8080/api/sections/basic/1001?academicYear=2025-2026-1"

# 2) 保存草稿：只提交学生可编辑字段
curl -X PUT "http://127.0.0.1:8080/api/sections/basic/1001" \
  -H "Content-Type: application/json" -H "X-User-Role: STU" -H "X-User-Id: 1001" \
  -d '{"academicYear":"2025-2026-1","fields":{"phone":"13800138000","email":"lin@example.edu.cn"}}'

# 3) 越权写：学号由学校维护，应返回 403 / EC-40301，details 指出 basic.studentNo
curl -X PUT "http://127.0.0.1:8080/api/sections/basic/1001" \
  -H "Content-Type: application/json" -H "X-User-Role: STU" \
  -d '{"academicYear":"2025-2026-1","fields":{"studentNo":"2023010199"}}'

# 4) 提交：必填未齐时返回 422 / EC-42202，details 列出缺失字段
curl -X POST "http://127.0.0.1:8080/api/sections/basic/1001/submit?academicYear=2025-2026-1" \
  -H "X-User-Role: STU"

# 5) 身份证明文：仅辅导员可看，调用后 arc_change_log 与 sys_audit_log 各多一条
curl "http://127.0.0.1:8080/api/sections/basic/1001/fields/basic.idCard/reveal?academicYear=2025-2026-1" \
  -H "X-User-Role: COU" -H "X-User-Id: 9001"

# 6) 提交成功后再撤回：X-User-Id 必须等于 studentId，第二次撤回返回 409 / EC-40903
curl -X POST "http://127.0.0.1:8080/api/sections/basic/1001/withdraw?academicYear=2025-2026-1" \
  -H "X-User-Role: STU" -H "X-User-Id: 1001"

# 7) 待确认值确认：辅导员把学生改过的手机号落为正式值（无待确认值时返回 409）
curl -X POST "http://127.0.0.1:8080/api/sections/basic/1001/fields/basic.phone/confirm?academicYear=2025-2026-1" \
  -H "X-User-Role: COU" -H "X-User-Id: 9001"

# 8) 待审列表：辅导员只看本班；管理员无临时授权时返回 403 / EC-40302
curl "http://127.0.0.1:8080/api/review/tasks?sectionCode=basic&status=SUBMITTED&academicYear=2025-2026-1" \
  -H "X-User-Role: COU" -H "X-User-Id: 9001"

# 9) 审核通过：非本班辅导员返回 403 / EC-40302；退回时理由不足 10 字返回 422 / EC-42203
curl -X POST "http://127.0.0.1:8080/api/review/basic/1001/approve" \
  -H "Content-Type: application/json" -H "X-User-Role: COU" -H "X-User-Id: 9001" \
  -d '{"academicYear":"2025-2026-1","comment":"材料齐全，同意通过"}'

curl -X POST "http://127.0.0.1:8080/api/review/basic/1001/reject" \
  -H "Content-Type: application/json" -H "X-User-Role: COU" -H "X-User-Id: 9001" \
  -d '{"academicYear":"2025-2026-1","reason":"紧急联系人电话与本人电话重复，请核对后重新提交"}'
```

> 审核通过/退回要看班级归属：需先有 `basic.classId` 且 `org_class.counselor_user_id` 等于当前 `X-User-Id`，
> 否则一律 `403 / EC-40302`（宁可拒绝，也不让非本班放行）。

## 九、扩展指引

**实现一个板块的业务**（以基本信息为例）：

1. 补建表脚本（`arc_profile` 等），板块实例统一用 `common/profile/entity/ArcProfile`，不要各板块复制一份。
2. 在 `XxxSectionServiceImpl` 里实现详情与保存，遵守「仅 DRAFT / RETURNED 可写」。
3. 字段校验优先走定义表标识，DTO 注解只做接口层兜底。
4. 提交时先做板块级校验，再调用 `ProfileWorkflowService.submit(...)`；**不要自己改 `status`、不要自己写状态日志**。
5. 用 curl 或集成测试验证正常路径、校验失败、状态冲突三类分支。

**加字段**：只加一条 `arc_field_def` 数据（含 validator 与 rule_json），并在 `openapi.yaml` 的示例里补值；
后端 DTO、校验器、前端渲染都不需要改——保存接口的入参是 `{academicYear, fields}` 映射。

**加板块**：复制任一同构板块的类改名，在 `SectionCodeEnum` 加枚举，路由自动可用；
审核无需另写，接入 `ProfileWorkflowService` 即可被 `/review/**` 纳管。

**加复杂查询**：在 `src/main/resources/mapper/<板块>/` 下建 XML，禁止在 Service 里拼 SQL。

## 十、注意事项

- **启动不依赖数据库**。连接池懒加载，MySQL 未起也能启动；此时 `/api/actuator/health` 的 `db` 为 `DOWN`，属正常现象。
- **501 不是缺陷**。六个板块的业务方法体统一抛 `BusinessException(ResultCode.NOT_IMPLEMENTED, ...)`。
- **跨域**：后端未配置 CORS，前端通过 Vite 代理走同源请求。不要用浏览器直连 `8080`，若确需跨域，另加 `CorsConfiguration` 并写进契约说明。
- **`target/` 已被忽略**，不要提交；`mvn clean` 会清掉产物。
- **端口占用**：`SERVER_PORT` 覆盖即可，不要改 `application.yml` 里的默认值。
- **中文乱码**：先确认库表是 `utf8mb4`，再看连接串是否带 `characterEncoding=utf8`。
- **不要换库**：改用 PostgreSQL 需先在需求文档 `DC-03` 确认，确认前一律按 MySQL 实现。
- **鉴权未接入**：读接口与保存接口仍不校验「当前用户是否该学生/该班辅导员」，任何调用方都能读写任意 `studentId`；
  撤回、审核、待确认处理已强制校验本人/本班。接统一认证后必须把同样的校验补到读与写，否则不要上线。

## 十一、技术约定

1. **模块边界**：新模块放 `com.college.growth.<module>`，结构对齐现有板块；跨板块只经 Service 接口。
2. **命名避让**：文档 9.1 的 `config` 业务模块落地时命名 `fieldconfig`，避免与 Spring 配置包 `com.college.growth.config` 冲突。
3. **字段校验**：规则以 `arc_field_def.validator` 标识存储，代码按标识注册校验器，禁止把某字段规则硬编码在接口里。
4. **错误码**：只从 `ResultCode` 取，不新增散落字符串；新增码值须同步 `openapi.yaml` 与需求文档 6.1。
5. **状态流转**：严格按五状态机，不新增状态；只能调用 `ProfileWorkflowService`，禁止板块直接改 `status`。
   状态变更、变更日志（`arc_change_log`）与审计日志（`sys_audit_log`）必须同一事务（BR-S11）。
6. **数据访问**：单表用 MyBatis-Plus，复杂查询写 XML；禁止在 Service 拼 SQL。
7. **敏感数据**：身份证、手机、压力自评、重大事件按文档 `BR-D05` / `BR-D07` 在接口层剔除或脱敏；日志禁止打印明文敏感字段。
8. **数据库脚本**：`src/main/resources/db/sql/{01}_{动作}_{对象}.sql`，两位数字前缀表示执行顺序，连续不跳号，已执行脚本禁止修改。
9. **注释引用**：业务代码注释标注需求编号（如 `S-1-05`、`BR-S02`、`EC-42202`），便于回溯与测试对齐。
