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
- **认证**：账号密码登录换 JWT，令牌校验统一在 `JwtAuthFilter` 完成，业务代码只读 `UserContext`，不自己解析令牌。

后端**不负责**：页面渲染、表单交互、前端路由。前后端只通过 `openapi.yaml` 约定的 HTTP 接口耦合。

## 二、技术栈

| 项 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 17 |
| 框架 | Spring Boot（Web / Validation / Actuator / Data Redis） | 3.2.12 |
| 持久层 | MyBatis-Plus（`mybatis-plus-spring-boot3-starter`） | 3.5.7 |
| 驱动 | MySQL Connector/J | 随 Boot 管理 |
| 数据库 | MySQL，字符集 `utf8mb4`，排序 `utf8mb4_0900_ai_ci` | 8.0.32+ |
| 缓存 | Redis（令牌吊销名单；字典缓存、任务锁预留） | 7.x |
| 构建 | Maven，单模块 | 3.9+ |
| 辅助 | Lombok、spring-boot-starter-test | — |

产物：`target/student-growth-platform.jar`。代码规模：155 个主代码类 + 10 个测试类（79 条用例）。

## 三、目录结构

```
backend/
├── pom.xml                                   # 构建：Spring Boot 3.2.12，单模块
├── src/
│   ├── main/
│   │   ├── java/com/college/growth/
│   │   │   ├── GrowthPlatformApplication.java   # 启动入口
│   │   │   ├── auth/                            # 登录鉴权（账号、JWT、吊销名单，见第六节）
│   │   │   ├── common/                          # 平台公共层（六板块复用）
│   │   │   │   ├── constant/                    # 通用常量（traceId 键名、板块数量）
│   │   │   │   ├── context/                     # CurrentUser、UserContext（由令牌填充）
│   │   │   │   ├── enums/                       # SectionCodeEnum、ProfileStatusEnum、UserRoleEnum
│   │   │   │   ├── exception/                   # BusinessException、GlobalExceptionHandler
│   │   │   │   ├── field/                       # 字段定义、校验器注册表、脱敏规则
│   │   │   │   ├── log/                         # 变更日志（arc_change_log）与审计日志（sys_audit_log）
│   │   │   │   ├── org/                         # 班级实体、学生班级与审核权解析
│   │   │   │   ├── profile/                     # 板块实例/字段值、值编解码、待确认区服务
│   │   │   │   ├── result/                      # Result、ResultCode、FieldErrorDetail、PageResult
│   │   │   │   ├── student/                     # 学生主数据（SIS 落地副本）与字段同步
│   │   │   │   ├── util/                        # 文本规范化、学年计算
│   │   │   │   └── web/                         # TraceIdFilter、JwtAuthFilter（令牌校验与身份注入）
│   │   │   ├── config/                          # JacksonConfig、MybatisPlusConfig、SchedulingConfig
│   │   │   ├── review/                          # 公共审核模块（六板块共用，见第六节）
│   │   │   ├── completion/                      # 完成标记（BR-S10）：判定、重算、定时补偿
│   │   │   ├── basic/                           # 板块一 基本信息
│   │   │   ├── academic/                        # 板块二 学业发展
│   │   │   ├── interest/                        # 板块三 兴趣特长
│   │   │   ├── life/                            # 板块四 生活心理
│   │   │   ├── career/                          # 板块五 职业规划
│   │   │   └── mentor/                          # 板块六 导师寄语
│   │   └── resources/
│   │       ├── application.yml                   # 端口、数据源、Redis、JWT、Jackson、MyBatis-Plus、日志
│   │       ├── db/sql/                           # 建表与种子脚本，按 01 → 09 顺序执行
│   │       └── mapper/review/                    # 待审列表 SQL（复杂查询走 XML）
│   └── test/java/com/college/growth/
│       ├── auth/service/                         # PasswordHasher（5）、JwtService（7）、AuthService（8）
│       ├── basic/service/                        # 板块一详情、保存、提交、越权与校验分支
│       ├── common/                               # 字段校验器、班级归属、待确认区、SIS 同步、文本规范化
│       ├── completion/service/                   # 完成标记：判定口径、幂等重算、权限与整学年统计
│       └── review/service/                       # 状态机：提交、撤回、通过、退回
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
  → JwtAuthFilter（校验 Authorization: Bearer，解析身份写入 UserContext；失败即 401）
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
| `ResultCode` | 业务码枚举，含 HTTP 状态与默认文案，共 21 个码值 |
| `BusinessException` | 业务异常，携带 `ResultCode` 与自定义文案 |
| `GlobalExceptionHandler` | 参数类 400、未登录 401、字段校验 422、状态冲突 409、未捕获异常 500，字段校验附 `details` |
| `TraceIdFilter` | 每请求生成 32 位 traceId，写入 MDC 与 `X-Trace-Id` 响应头 |
| `JwtAuthFilter` | 校验 Bearer 令牌并把身份注入 `UserContext`，白名单仅 `/auth/login` 与 `/actuator` |
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

建档是跨板块能力（一次生成六条实例），同样单独一组路径：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/profiles/classes?academicYear=` | 本班建档进度：在读人数、已建档人数、待建档人数 |
| POST | `/api/profiles/batch-create` | 按学年 + 班级批量生成六板块空实例，已存在跳过（EX-01） |

毕业归档同样是跨板块能力（一次流转六条实例），单独一组路径：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/archive/candidates?academicYear=&classCode=` | 本班归档名单：六板块完成情况、可归档 / 已归档判定 |
| POST | `/api/archive/batch` | 批量归档：全部通过才转 `LOCKED`，任一未完成整批拒绝（EX-12） |

完成标记是「学生 × 学年」级别的派生结论，同样单独一组路径：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/completion/students/{studentId}?academicYear=` | 读取六板块完成标记与逐板块状态（固定六项，缺块为「未建档」） |
| POST | `/api/completion/refresh?studentId=&academicYear=` | 手动重算；省略 `studentId` 即整学年（仅持授权管理员），幂等，写 `COMPLETION` 审计 |

> **当前状态：板块一（basic）后端闭环（7 个板块级接口）；审核模块 3 个接口可用；登录鉴权 3 个接口可用；批量建档 2 个接口可用；批量归档 2 个接口可用；完成标记 2 个接口可用**；
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

### 批量建档模块（`com.college.growth.enroll`）

| 类 | 职责 |
|---|---|
| `ProfileEnrollService` | 按班级取在读学生，逐板块补齐缺失实例；已存在的一律跳过、不覆盖 |
| `ProfileEnrollController` | `/api/profiles/**` 两个接口 |

口径要点：

- 只允许辅导员调用，且只能处理本人带班班级（`org_class.counselor_user_id`）；管理员为只读授权，返回 `EC-40302`；
- 学生清单来自 `stu_student`（在读），请求体不传学生 ID，避免越权建档；
- 只建 `arc_profile`，不写 `arc_profile_item`、不触发审核；字段值仍由各板块保存接口与学工同步写入。

### 毕业归档模块（`com.college.growth.archive`）

| 类 | 职责 |
|---|---|
| `ArchiveService` | 归档名单与整批归档：口径判定、整批拒绝（EX-12）、终态流转与留痕 |
| `ArchiveController` | `/api/archive/**` 两个接口 |

口径要点：

- 「完成」= 六板块齐全且每块 `APPROVED` 或 `LOCKED`；已归档也计入，保证重复提交幂等；
- 整批语义：先全量校验再统一写库，名单里只要一人未完成就谁都不动，返回未完成清单；
- 阻断原因用 `Result.data.rejected` 表达而不是 HTTP 409——拒绝原因是一份清单，塞进错误体只能压成字符串；
- 归档时把 `completed` 显式置为真，避免「全部通过」的结论在归档当天被重算清零；
- 仅辅导员可写，且只能归档本班学生；管理员持临时授权只读，学生不涉及。

### 完成标记模块（`com.college.growth.completion`）

| 类 | 职责 |
|---|---|
| `CompletionService` | 唯一完成判定：`APPROVED` / `LOCKED` 按板块编码去重后凑满六块才算完成 |
| `CompletionServiceImpl` | 幂等重算（只在标记翻转时写库）、权限判定、整学年统计 |
| `CompletionRefreshTask` | 每天 03:30 重算当前学年，补偿绕过状态机的旁路数据 |
| `CompletionController` | `/api/completion/**` 两个接口 |

三条入口共用一套口径，都不允许自己数板块：

1. **状态流转**：`ProfileWorkflowServiceImpl` 在提交 / 撤回 / 通过 / 退回后调用 `CompletionService.refresh(...)`，与流转同事务；
2. **定时补偿**：`CompletionRefreshTask` 扫当前学年，只处理直接改库、修数脚本、迁移导入这类没走状态机的数据；
3. **手动重算**：`POST /api/completion/refresh`，运维补偿用，每次写一条审计日志。

标记写在六条 `arc_profile.completed` 上（「学生 × 学年」级别冗余），更新用 `lambdaUpdate` 而不走 `updateById`——
派生标记不参与乐观锁，否则会和正在编辑的板块抢版本号触发 `EC-40907`。
**已归档（`LOCKED`）计为完成**：归档是完成之后的终态，不能反过来把标记算回未完成。
**与归档的衔接**：`ArchiveServiceImpl` 归档时显式把 `completed` 置真，即便归档动作本身不触发状态机重算也不会被算回未完成。

### 登录鉴权模块（`com.college.growth.auth`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 账号密码换令牌，**唯一的匿名业务接口** |
| GET | `/api/auth/me` | 查询当前登录用户，供前端刷新后恢复登录态 |
| POST | `/api/auth/logout` | 把令牌 `jti` 写入吊销名单，立即失效 |

| 类 | 职责 |
|---|---|
| `AuthController` | `/api/auth/**` 三个接口 |
| `AuthService` | 登录校验（账号不存在也跑一次假哈希，避免时序泄露）、签发令牌、登出、刷新登录时间 |
| `JwtService` | HS256 签发与解析，载荷 `sub/role/grant/name/jti/iat/exp`；密钥不足 32 字符启动即失败 |
| `PasswordHasher` | PBKDF2WithHmacSHA256，格式 `pbkdf2$迭代$salt$hash`，`MessageDigest.isEqual` 定长比对 |
| `TokenBlacklistService` | Redis 存被吊销的 `jti`（TTL = 剩余有效期）；Redis 不可用时 fail-open，不阻断业务 |

当前用户身份**只来自令牌**，`UserContext` 由 `JwtAuthFilter` 填充；请求头 `X-User-Id` / `X-User-Role` / `X-Temp-Grant`
已随骨架期一起移除，再发送也不会生效。角色差异仍在业务侧判定：

| 角色 | 数据范围 |
|---|---|
| `STU` | 只能读写本人档案（`studentId` 取自令牌） |
| `COU` | 按 `org_class.counselor_user_id` 判定本班 |
| `ADMIN` | 默认无业务数据权限，`sys_user.temp_grant = 1` 才可只读（否则 `EC-40302`） |

演示账号由 `09_insert_user_seed.sql` 写入，密码统一 `Passw0rd!`：

| 登录名 | 角色 | 说明 |
|---|---|---|
| `2023010101` | STU | 学生，关联 `studentId=1001` |
| `2023010102` / `2023010201` | STU | 另两个班的学生 |
| `9001` / `9002` | COU | 辅导员，各带一个班 |
| `9003` | ADMIN | 持临时授权（`temp_grant=1`），可读业务数据 |
| `9004` | ADMIN | 无授权，用于验证 `EC-40302` |

令牌默认 8 小时有效，可通过 `JWT_TTL_HOURS` 调整；签名密钥取 `JWT_SECRET`，**生产必须覆盖**，
`application.yml` 里的默认值仅供本地联调。密码摘要算法与迭代次数变更后老账号需重置密码（无平滑迁移）。

业务码按类别分段，明细只在契约与 `ResultCode` 里维护，此处不重复：

| 段位 | 含义 | 典型码 |
|---|---|---|
| `EC-400xx` | 参数与请求格式 | `EC-40001` / `EC-40002` / `EC-40003` |
| `EC-401xx` | 登录与令牌 | `EC-40101` / `EC-40102` / `EC-40103` / `EC-40104` / `EC-40105` |
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
| `JWT_SECRET` | 32 位开发用默认值 | 令牌签名密钥，**生产必须覆盖**；少于 32 字符启动失败 |
| `JWT_TTL_HOURS` | `8` | 令牌有效期（小时），同时决定吊销名单的 TTL 上限 |
| `LOG_LEVEL_APP` | `debug` | 业务包日志级别，生产建议 `info` |
| `COMPLETION_REFRESH_ENABLED` | `true` | 完成标记定时补偿开关；多实例部署时可只在一台上开启 |
| `COMPLETION_REFRESH_CRON` | `0 30 3 * * ?` | 补偿任务执行时间，Spring 6 段 cron |

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

建库脚本在 `src/main/resources/db/sql/`，九个文件按文件名前缀数字 **01 → 09** 顺序执行；脚本幂等，可重复跑：

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
# 08 账号表 sys_user（登录、角色、学生关联、临时授权位）
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/08_create_user_table.sql
# 09 账号种子（7 个演示账号 + 1 个无授权管理员，密码统一 Passw0rd!）
mysql -uroot -proot --default-character-set=utf8mb4 student_growth < src/main/resources/db/sql/09_insert_user_seed.sql

# 自检：应为 total=16、required_cnt=8、sis_mapped=7、students=4、users=8
mysql -uroot -proot --default-character-set=utf8mb4 student_growth -e \
  "select count(*) total, sum(required=1) required_cnt, sum(source_field is not null) sis_mapped from arc_field_def where section_code='BASIC';
   select count(*) students from stu_student;
   select count(*) users from sys_user;"
```

> **存量库升级**：老库没有 `arc_field_def.source_field` 列，先跑 `05`（先 ALTER 再回填，可重复执行），
> 再跑 `06`/`07`。`03` 不涉及这一列，无需重跑；`01` 只对全新库生效（`CREATE TABLE IF NOT EXISTS`）。

> 学生主数据的值与档案的关系：`stu_student` 是权威副本，`StudentSyncService` 在**建档、保存、提交**时
> 把 SIS 字段物化进 `arc_profile_item`（只补空值，不覆盖已有值）。学工系统改正数据后用
> `POST /api/sections/basic/{studentId}/sync-student` 强制覆盖，仅本班辅导员或持授权管理员可调用。

> **必须带 `--default-character-set=utf8mb4`**：中文 Windows 的 mysql 客户端默认 `gbk`，而脚本是 UTF-8，
> 不指定时中文 COMMENT 转换失败会导致整条 `CREATE TABLE` 被跳过（现象是"执行成功但表没建出来"）。

> Windows 下在 Git Bash 或 IDEA 的 Database 控制台执行；若库尚未创建，先 `CREATE DATABASE student_growth DEFAULT CHARSET utf8mb4;`。
> 暂未引入 Flyway：脚本是普通 SQL，按 `01 → 09` 顺序手工执行。将来接入 Flyway 时，改名为 `V1__`、`V2__` 前缀并移入 `db/migration` 即可纳管。

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
BASE=http://127.0.0.1:8080/api

# 0) 健康检查：除 /auth/login 外唯一免令牌的入口
curl $BASE/actuator/health

# 1) 登录拿令牌（演示账号密码统一 Passw0rd!）。学生看本人、辅导员看本班、管理员需授权
login() { curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" \
  -d "{\"username\":\"$1\",\"password\":\"Passw0rd!\"}" \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'; }
STU=$(login 2023010101); COU=$(login 9001); ADMIN0=$(login 9004)
echo "令牌长度 ${#STU}"

# 2) 当前登录用户：刷新页面后前端靠它恢复登录态
curl $BASE/auth/me -H "Authorization: Bearer $STU"

# 3) 不带令牌：401 / EC-40101；伪造 X-User-Role 头同样无效（身份只来自令牌）
curl "$BASE/sections/basic/1001?academicYear=2025-2026-1"

# 4) 详情：未建档时返回 DRAFT + 16 条 fieldMeta，fields 为空
curl "$BASE/sections/basic/1001?academicYear=2025-2026-1" -H "Authorization: Bearer $STU"

# 5) 保存草稿：只提交学生可编辑字段
curl -X PUT "$BASE/sections/basic/1001" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $STU" \
  -d '{"academicYear":"2025-2026-1","fields":{"phone":"13800138000","email":"lin@example.edu.cn"}}'

# 6) 越权写：学号由学校维护，应返回 403 / EC-40301，details 指出 basic.studentNo
curl -X PUT "$BASE/sections/basic/1001" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $STU" \
  -d '{"academicYear":"2025-2026-1","fields":{"studentNo":"2023010199"}}'

# 7) 提交：必填未齐时返回 422 / EC-42202，details 列出缺失字段
curl -X POST "$BASE/sections/basic/1001/submit?academicYear=2025-2026-1" \
  -H "Authorization: Bearer $STU"

# 8) 身份证明文：仅辅导员可看，调用后 arc_change_log 与 sys_audit_log 各多一条
curl "$BASE/sections/basic/1001/fields/basic.idCard/reveal?academicYear=2025-2026-1" \
  -H "Authorization: Bearer $COU"

# 9) 撤回：只有学生本人能撤回，第二次撤回返回 409 / EC-40903
curl -X POST "$BASE/sections/basic/1001/withdraw?academicYear=2025-2026-1" \
  -H "Authorization: Bearer $STU"

# 10) 待确认值确认：辅导员把学生改过的手机号落为正式值（无待确认值时返回 409）
curl -X POST "$BASE/sections/basic/1001/fields/basic.phone/confirm?academicYear=2025-2026-1" \
  -H "Authorization: Bearer $COU"

# 11) 待审列表：辅导员只看本班；无授权管理员 9004 返回 403 / EC-40302
curl "$BASE/review/tasks?sectionCode=basic&status=SUBMITTED&academicYear=2025-2026-1" \
  -H "Authorization: Bearer $COU"
curl "$BASE/review/tasks?academicYear=2025-2026-1" -H "Authorization: Bearer $ADMIN0"

# 12) 审核通过 / 退回：非本班辅导员 403 / EC-40302；退回理由不足 10 字 422 / EC-42203
curl -X POST "$BASE/review/basic/1001/approve" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $COU" \
  -d '{"academicYear":"2025-2026-1","comment":"材料齐全，同意通过"}'

curl -X POST "$BASE/review/basic/1001/reject" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $COU" \
  -d '{"academicYear":"2025-2026-1","reason":"紧急联系人电话与本人电话重复，请核对后重新提交"}'

# 13) 登出后复用同一令牌：401 / EC-40101（jti 已进吊销名单）
curl -X POST $BASE/auth/logout -H "Authorization: Bearer $STU"
curl $BASE/auth/me -H "Authorization: Bearer $STU"
```

> **学年要显式传**：不传时后端取当前学年（如 `2026-2027-1`），而种子数据在 `2025-2026-1`，
> 待审列表会因学年不同返回空页——这不是权限问题。
> **管理员数据权限**：`9003` 持临时授权可读业务数据，`9004` 无授权，正好用来对比 `EC-40302`。
> **审核归属**：需先有 `basic.classId` 且 `org_class.counselor_user_id` 等于该辅导员的 `sys_user.id`，
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
- **登录已接入，但数据权限要分两步看**：请求层由 `JwtAuthFilter` 统一拦未登录（`EC-40101` / `EC-40102`）；
  业务层再由各 Service 校验「这个 `studentId` 是不是你本人 / 你班」。过了鉴权不等于有数据权限，
  新增接口时两步都不能省。
- **令牌密钥必须覆盖**：`JWT_SECRET` 的默认值只够本地联调，生产环境不覆盖等于任何人都能自签令牌。
- **吊销名单 fail-open**：Redis 不可用时登出不会立即生效（令牌最多活到 `exp`），这是为可用性做的取舍；
  不要把校验整体去掉来「解决」Redis 抖动。

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
10. **认证与授权**：新增接口默认要求令牌（`JwtAuthFilter` 全局生效），确需匿名时在 `PUBLIC_PATHS` 显式登记并说明理由；
    授权一律在 Service 层用 `UserContext` 判定，禁止把权限判断写进 Controller 或前端。
11. **密码与令牌**：口令只存 PBKDF2 摘要（`pbkdf2$迭代$salt$hash`），任何日志、异常文案、审计记录不得出现明文或摘要；
    令牌只经 `Authorization` 头传递，不落 URL、不落 Cookie。
