# 学生成长档案平台 · 前端

> Vue 3 + Vite 单页应用，承载六大板块的填写、查看与提交流程。

本目录是前端源码，与 [backend/](../backend/README.md) 中的 Spring Boot 服务配套。项目总览见[根 README](../README.md)，接口契约见 [openapi.yaml](../openapi.yaml)。

## 一、职责与边界

前端负责：

- **六板块页面**：一板块一路由一视图，展示板块状态、字段值与可编辑性。
- **表单交互**：按后端下发的字段渲染表单，做输入提示与就地错误展示。
- **请求封装**：统一按 `Result` 解包，把业务码转成可直接展示的错误。
- **联调支撑**：后端未实现时用 MSW 提供 mock，保证页面先跑通。

前端**不负责**：业务规则判定（能否编辑、能否提交以 `editable` 与业务码为准）、字段校验的最终裁决（以后端为准）、权限与脱敏（后端在接口层处理）。

## 二、技术栈与依赖

| 项 | 选型 | 声明版本 | 实际锁定 |
|---|---|---|---|
| 框架 | Vue 3（`<script setup>` + TS） | `^3.5.13` | 3.5.46 |
| 路由 | Vue Router | `^4.4.5` | 4.6.5 |
| 请求 | Axios | `^1.7.7` | 1.21.1 |
| 构建 | Vite | `^5.4.10` | 5.4.21 |
| 插件 | `@vitejs/plugin-vue` | `^5.1.4` | 5.2.4 |
| 语言 | TypeScript | `~5.6.3` | 5.6.3 |
| 类型检查 | `vue-tsc` | `^2.1.10` | 2.2.12 |
| Mock | MSW | `^2.4.9` | 2.15.0 |
| 脚本运行 | `tsx`（跑 smoke 测试） | `^4.23.15` | 4.23.15 |

未引入 UI 组件库与状态管理库：骨架期样式手写，跨页状态目前只有「学生 ID + 学年」两个常量，接入登录后再评估是否引入 Pinia。

### 环境变量

| 变量 | 位置 | 默认 | 说明 |
|---|---|---|---|
| `VITE_API_BASE_URL` | `.env.development` | `/api` | 接口前缀，与后端 `context-path` 一致 |
| `VITE_USE_MOCK` | `.env.development` | `true` | `true` 启用 MSW，`false` 走真实后端 |
| `VITE_BACKEND_ORIGIN` | 启动时环境变量 | `http://127.0.0.1:8080` | Vite 代理目标，改后端端口时用 |

`.env.development` 需要提交（团队共用的 mock 开关），`.env.local` 已在根 `.gitignore` 中忽略，用于本机覆盖。

### 构建配置要点（`vite.config.ts`）

- `resolve.alias`：`@` → `src`，与 `tsconfig.json` 的 `paths` 对应。
- `server.port`：`5173`。
- `server.proxy`：`/api` 转发到 `VITE_BACKEND_ORIGIN`，**不重写路径**（后端 context-path 本身就是 `/api`）。

## 三、目录结构

```
frontend/
├── public/
│   └── mockServiceWorker.js   # MSW 拦截脚本，由 npx msw init 生成，随仓库提交
├── scripts/
│   └── smoke-mock.mts         # mock 冒烟测试（14 项断言）
├── src/
│   ├── api/
│   │   ├── constants.ts       # 成功码 '0'、接口前缀
│   │   ├── types.ts           # Result / PageResult / SectionVO / 状态类型
│   │   ├── http.ts            # axios 实例、Result 解包、ApiError
│   │   └── sections.ts        # 六板块三接口：详情 / 存草稿 / 提交
│   ├── mocks/
│   │   ├── data.ts            # 内存数据，字段对齐各板块 SaveDTO
│   │   ├── handlers.ts        # 六板块 × 三接口的 mock 实现
│   │   └── browser.ts         # Service Worker 启动
│   ├── router/index.ts        # 六板块路由 + 404
│   ├── views/
│   │   ├── HomeView.vue       # 板块总览
│   │   ├── SectionShell.vue   # 板块页公共外壳（加载 / 表单 / 保存 / 提交）
│   │   ├── NotFoundView.vue   # 404
│   │   └── sections/          # 六个板块视图，各自独立文件
│   ├── sectionMeta.ts         # 板块名称、字段清单、状态文案、演示参数
│   ├── assets/main.css        # 全局样式与状态标签配色
│   ├── App.vue                # 布局、顶部导航、页脚
│   ├── main.ts                # 入口：先初始化 mock，再挂载
│   └── env.d.ts               # 环境变量与 .vue 模块类型
├── .env.development
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## 四、主要页面与组件

### 路由

| 路径 | 名称 | 组件 | 板块编码 | 说明 |
|---|---|---|---|---|
| `/` | `home` | `HomeView.vue` | — | 六板块入口卡片 |
| `/sections/basic` | `section-basic` | `sections/BasicSectionView.vue` | `basic` | 基本信息 |
| `/sections/academic` | `section-academic` | `sections/AcademicSectionView.vue` | `academic` | 学业发展 |
| `/sections/interest` | `section-interest` | `sections/InterestSectionView.vue` | `interest` | 兴趣特长 |
| `/sections/life` | `section-life` | `sections/LifeSectionView.vue` | `life` | 生活心理 |
| `/sections/career` | `section-career` | `sections/CareerSectionView.vue` | `career` | 职业规划 |
| `/sections/mentor` | `section-mentor` | `sections/MentorSectionView.vue` | `mentor` | 导师寄语 |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundView.vue` | — | 404 |

路由按板块拆分组件（而不是一个通用页取参数），两人各自负责的板块落在不同文件，改动不冲突。

### 组件

| 组件 | 类型 | 作用 |
|---|---|---|
| `App.vue` | 布局 | 顶部导航（总览 + 六板块）、`<RouterView>` 容器、页脚提示 |
| `HomeView.vue` | 页面 | 六板块卡片，显示名称、一句话说明与接口路径 |
| `SectionShell.vue` | 板块外壳 | 承接板块全部交互：加载详情、渲染表单、保存草稿、提交、错误与字段级提示、原始返回折叠面板 |
| `sections/*View.vue` | 六个板块页 | 只把 `code` 传给 `SectionShell`；板块特有交互在此文件内扩展 |
| `NotFoundView.vue` | 页面 | 404 |

### 表单渲染机制

页面**不硬编码表单字段**，由 `sectionMeta.ts` 驱动：

- 每个板块定义 `fields: [{ key, label, type }]`，`key` 与后端 `SaveDTO` 属性名一致。
- `type` 支持 `text` / `textarea` / `number` / `date` / `tags`；`tags` 在页面上以逗号分隔输入，提交时转成数组。
- 字段值取自接口返回的 `fields`，提交时按同样结构组装回 `SaveDTO` 形状，并补 `academicYear`。

后端实现字段定义表后，把 `sectionMeta.ts` 改为从接口读取即可，页面结构不用改。

### 数据流

```
SectionShell.vue
  → api/sections.ts（fetchSection / saveSection / submitSection）
  → api/http.ts（axios，baseURL=/api）
  → Result 解包：code === '0' 返回 data；否则抛 ApiError(code / message / details / traceId)
  → 组件：成功刷新详情；失败按 code 展示提示，details 映射为字段下方红字
```

## 五、开发与构建命令

命令均在 `frontend/` 下执行：

| 命令 | 作用 |
|---|---|
| `npm install` | 安装依赖（首次，或 `package.json` 变更后） |
| `npm run dev` | 启动开发服务器，http://localhost:5173 |
| `npm run build` | `vue-tsc --noEmit` 类型检查 + Vite 生产构建，产物在 `dist/` |
| `npm run type-check` | 仅类型检查，不构建 |
| `npm run preview` | 本地预览 `dist/` 产物 |
| `npm run smoke:mock` | 用 `msw/node` 复跑 mock handlers，校验响应结构与错误码（14 项断言） |

改动 `src/mocks/` 或 `src/api/types.ts` 后建议跑一次 `smoke:mock`，能挡住大部分契约漂移。

## 六、mock 与切换真实接口

默认 `VITE_USE_MOCK=true`，`main.ts` 在挂载前启动 Service Worker，`/api/**` 由 `mocks/handlers.ts` 接管，因此**不启动后端也能跑通全部页面**。

切换到真实后端：

1. 启动后端（见 [backend/README.md](../backend/README.md)）；
2. 本机新建 `.env.local`，写入 `VITE_USE_MOCK=false`；
3. 重启 `npm run dev`。

请求经 Vite 代理到 `http://127.0.0.1:8080/api`。后端业务接口目前返回 `EC-50000`，页面会显示对应提示，属预期。

mock 覆盖：六板块的详情、保存、提交，以及 `EC-42201`（字段校验）、`EC-40002`（缺参）、`EC-40003`（非法板块）、`EC-40902`（状态冲突）四类错误。

## 七、注意事项

- **浏览器用 `http://localhost:5173`**。本机 Vite 可能只绑定 `::1`，用 `127.0.0.1` 会连接失败。
- **`node_modules/` 与 `dist/` 不入库**，已在根 `.gitignore` 忽略；不要为了「让同事直接跑」而提交它们。
- **不要硬编码表单字段**。新增字段只在 `sectionMeta.ts` 与后端 `SaveDTO` 两处加，页面自动渲染。
- **契约变更两边同步**。改字段名的顺序：`openapi.yaml` → 后端 `SaveDTO` → `sectionMeta.ts` → `mocks/data.ts`，最后跑 `smoke:mock`。
- **提交前至少跑 `npm run type-check`**；改了 mock 或类型再跑 `smoke:mock`。
- **无登录态**。学生 ID 与学年暂用 `sectionMeta.ts` 的 `DEMO_STUDENT_ID` / `DEMO_ACADEMIC_YEAR`；鉴权头预留 `Authorization: Bearer`，后端就绪后在 `http.ts` 统一注入。
- **错误展示分工**：`code` 与 `message` 直接展示，`traceId` 折叠展示供报障，`details` 按字段就位提示。
- **样式为骨架期手写**。引入 UI 组件库时先统一 `assets/main.css` 的变量，避免两套主题变量并存。

## 八、扩展指引

- **新增板块**：`router/index.ts` 加路由 → `views/sections/` 加视图（复用 `SectionShell`）→ `sectionMeta.ts` 加字段清单 → `mocks/handlers.ts` 与 `mocks/data.ts` 加数据。
- **板块特有交互**（附件上传、证书列表等）：写在对应 `sections/*View.vue` 内，公共外壳 `SectionShell.vue` 保持通用，避免影响其他板块。
- **新增接口**：`src/api/` 下按板块加函数，`handlers.ts` 补处理器，同步 `openapi.yaml`。
- **新增全局状态或登录**：先评估是否引入 Pinia；登录态建议放 `src/stores/`，并在 `api/http.ts` 的请求拦截器里注入令牌。
