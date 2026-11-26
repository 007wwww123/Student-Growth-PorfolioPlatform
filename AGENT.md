# 学生成长档案平台 · 项目级开发指南

本指南适用于整个仓库。平台按基本信息、学业发展、兴趣特长、生活心理、职业规划、导师寄语六个板块组织档案，每个板块独立填写、提交和审核。

## 开工前定位

| 任务 | 从哪里开始 |
| --- | --- |
| 确认一期范围、进度、协作方式 | [项目 README](README.md) |
| 调整接口路径、请求字段、响应或错误码 | [共享接口契约](openapi.yaml) |
| 修改业务、校验、数据访问 | [后端 README](backend/README.md)，再查 backend/src/main/java/com/college/growth 下对应板块 |
| 修改页面、请求封装或模拟数据 | [前端 README](frontend/README.md)，再查 frontend/src 下的 views、api 或 mocks |

当前为骨架阶段。前端可通过 MSW 演示详情、保存和提交。后端尚未实现的业务方法返回 HTTP 501、业务码 EC-50000。验收时分别说明验证的是模拟流程还是真实接口。当前缺少登录与数据范围控制，开发和演示使用虚构学生资料。

## 跨端修改

1. 先查 openapi.yaml。接口有变化时先改契约，单独提交契约变更，并在同一功能 PR 中同步两端实现。
2. 后端同步受影响的 DTO、VO 和接口。沿用 Result 响应包装，从 ResultCode 取业务码，保留 traceId 与字段错误 details。
3. 前端同步请求类型、frontend/src/sectionMeta.ts 的字段元数据及 mocks 下的数据和处理器。公共表单复用 SectionShell，板块特有交互放入对应视图。
4. 页面通过 api 层调用接口。开发代理保留 /api 前缀；真实接口联调时将 VITE_USE_MOCK 设为 false，重启 Vite。

## 后端业务约束

- 跨板块通过 Service 接口调用。单表操作沿用 MyBatis-Plus，复杂查询写 Mapper XML。
- 实现配置驱动校验时按 arc_field_def.validator 标识匹配规则。状态流转沿用现有五状态模型，状态更新和变更日志在同一事务中提交。
- 身份证、手机号、压力自评和重大事件按 BR-D05、BR-D07 在接口层剔除或脱敏，日志禁止记录明文。
- 数据库迁移放入 backend/src/main/resources/db/migration，沿用连续编号的 V{n}__{描述}.sql。已执行脚本的修正通过新迁移交付。

## 本地运行与验证

后端使用 JDK 17、Maven 3.9+，前端使用 Node.js 18+、npm 9+。下列命令均从仓库根目录执行。

| 目的 | 命令 |
| --- | --- |
| 安装前端锁定依赖 | `npm --prefix frontend ci` |
| 启动前端，默认使用 mock | `npm --prefix frontend run dev` |
| 启动本地 MySQL 和 Redis | `docker compose up -d` |
| 启动后端 | `mvn -f backend/pom.xml spring-boot:run` |
| 验证后端修改 | `mvn -f backend/pom.xml test` |
| 检查前端类型 | `npm --prefix frontend run type-check` |
| 验证 mock 或接口类型变更 | `npm --prefix frontend run smoke:mock` |
| 验证前端构建（含类型检查） | `npm --prefix frontend run build` |

页面入口为 http://localhost:5173，后端健康检查为 http://localhost:8080/api/actuator/health。交付时记录实际执行的检查及结果，说明仍未验证的业务路径。仓库按内部项目管理，对外分发须经学院书面同意。
