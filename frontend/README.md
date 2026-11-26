# 学生成长档案平台 · 前端

六大板块档案的前端骨架，与本仓库根目录的 Spring Boot 后端配套（后端见 `/README.md`）。

当前为骨架阶段：路由、接口封装、mock 数据已就绪，页面能跑通「查询 → 保存草稿 → 提交」全流程；
后端接口实现后关闭 mock 即可切换为真实接口，页面代码无需改动。

## 技术栈

| 项 | 选型 |
|---|---|
| 框架 | Vue 3（`<script setup>` + TypeScript） |
| 构建 | Vite 5 |
| 路由 | Vue Router 4 |
| 请求 | Axios（统一按 `Result` 约定解包） |
| Mock | MSW 2（Service Worker 拦截，保留真实网络行为） |

## 目录结构

```
frontend/
├── public/
│   └── mockServiceWorker.js   # MSW 生成的拦截脚本，随仓库提交
├── scripts/
│   └── smoke-mock.mts         # mock 处理器冒烟测试（14 项断言）
├── src/
│   ├── api/
│   │   ├── constants.ts       # 成功码、接口前缀
│   │   ├── types.ts           # Result / PageResult / SectionVO，与后端一一对应
│   │   ├── http.ts            # axios 实例 + Result 解包 + ApiError
│   │   └── sections.ts        # 六大板块接口（查详情 / 存草稿 / 提交）
│   ├── mocks/
│   │   ├── data.ts            # 内存数据，字段对齐各板块 SaveDTO
│   │   ├── handlers.ts        # 六板块 × 三接口的 mock 实现
│   │   └── browser.ts         # Service Worker 启动
│   ├── router/index.ts        # 六板块路由，一板块一视图
│   ├── views/
│   │   ├── HomeView.vue       # 板块总览
│   │   ├── SectionShell.vue   # 板块页公共外壳（表单由 sectionMeta 驱动）
│   │   └── sections/          # 六个板块视图，各自独立文件便于分工
│   ├── sectionMeta.ts         # 板块名称、字段清单、状态文案
│   ├── App.vue                # 布局与导航
│   └── main.ts                # 入口，启动前初始化 mock
└── .env.development           # 接口前缀与 mock 开关
```

## 本地运行

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

默认开启 mock（`VITE_USE_MOCK=true`），因此**不需要启动后端**也能看到完整页面。

### 切换到真实后端

1. 启动后端（见根目录 README，端口 8080）
2. 把 `.env.development` 里的 `VITE_USE_MOCK` 改为 `false`，或在本机新建 `.env.local` 覆盖：

```
VITE_USE_MOCK=false
```

`/api` 由 Vite proxy 转发到 `http://127.0.0.1:8080`（后端 context-path 同为 `/api`，不做重写）。
后端地址不同时，启动前设置 `VITE_BACKEND_ORIGIN`。

> 后端多数接口目前返回 `EC-50000 尚未实现`。关闭 mock 后会直接看到该提示，属于预期。

## 其他命令

```bash
npm run build       # vue-tsc 类型检查 + 生产构建（产出 dist/）
npm run type-check  # 仅类型检查
npm run smoke:mock  # 用 msw/node 跑一遍 mock 处理器，校验响应结构与错误码
```

`smoke:mock` 会在终端打印 14 项断言结果，改动 `handlers.ts` 或 `data.ts` 后建议先跑一次。

## 接口约定（前后端必须一致）

### 统一响应体

所有接口都包一层 `Result`，前端只按 `code` 分支：

```json
{
  "code": "0",
  "message": "操作成功",
  "traceId": "a1b2c3d4e5f60708",
  "timestamp": "2026-11-26T15:23:50+08:00",
  "data": {},
  "details": []
}
```

- `code === "0"` 为成功，其余为 `EC-xxxxx` 业务码
- `data` 仅在成功时出现（后端 `non_null` 序列化）
- `details` 仅在字段校验失败（`EC-42201`）时出现，结构为 `{ field, reason, message }`

`src/api/http.ts` 的 `request<T>()` 已完成解包：成功直接返回 `data`，失败抛 `ApiError`（含 `code` / `message` / `details` / `traceId`），页面无需判断 HTTP 状态码。

### 板块数据结构

**板块自身字段放在 `fields` 内，外层是公共信封**：

```json
{
  "studentId": 1001,
  "academicYear": "2025-2026-1",
  "sectionCode": "BASIC",
  "sectionName": "基本信息",
  "status": "DRAFT",
  "fields": { "name": "林清和", "studentNo": "2023010101" },
  "editable": true
}
```

这样后端可以按 `arc_field_def` 动态下发字段，前端表单跟着 `fields` 渲染，新增字段不需要改页面。
字段清单见 `src/sectionMeta.ts`，与各板块 `SaveDTO` 属性名保持一致。

### 状态取值

`DRAFT` 草稿 / `SUBMITTED` 已提交 / `RETURNED` 已退回 / `APPROVED` 已通过 / `LOCKED` 已归档，
与后端 `ProfileStatusEnum` 一致。仅 `DRAFT`、`RETURNED` 允许保存与提交。

## 扩展指引

**新增一个板块**：`router/index.ts` 加一条路由 → `views/sections/` 加一个视图 → `sectionMeta.ts` 加字段清单 → `mock/handlers.ts` 加默认数据。

**板块增加字段**：先让后端 `SaveDTO` 与 `openapi.yaml` 加上字段，再在 `sectionMeta.ts` 补一行，页面自动渲染。

**板块特殊交互**（附件上传、证书列表等）：在对应 `views/sections/XxxSectionView.vue` 内扩展，公共外壳 `SectionShell.vue` 不动，避免影响其他板块。

**新增组件**：放到 `src/components/`（当前骨架尚未需要）。

**新增接口**：`src/api/` 下按板块加函数，mock 在 `handlers.ts` 补对应处理器，并同步 `openapi.yaml`。

## 已知限制

- 无登录与鉴权，学生 ID 与学年暂用 `sectionMeta.ts` 里的 `DEMO_STUDENT_ID` / `DEMO_ACADEMIC_YEAR`，接入登录后从会话读取
- 字段校验规则前端只做了提示，真实校验以后端为准
- 未接入 UI 组件库，样式为骨架期手写，后续可按需引入
