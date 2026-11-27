# 学生成长档案平台 · 前端

> Vue 3 + Vite 单页应用，承载六大板块的填写、查看与提交流程。

本目录是前端源码，与 [backend/](../backend/README.md) 中的 Spring Boot 服务配套。项目总览见[根 README](../README.md)，接口契约见 [openapi.yaml](../openapi.yaml)。

## 一、职责与边界

前端负责：

- **六板块页面**：一板块一路由一视图，展示板块状态、字段值与可编辑性；支持提交与撤回。
- **审核工作台**：待审列表（按班级、板块、学年筛选）与审核详情，通过 / 退回理由回传、待确认值处理、敏感字段明文查看。
- **登录与守卫**：登录页、令牌存储、路由守卫（未登录跳登录、角色不足跳 403）、令牌失效统一拦截。
- **表单交互**：按后端下发的 `fieldMeta` 渲染表单，做输入提示与就地错误展示。
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

未引入 UI 组件库与状态管理库：样式手写；登录态是「一个令牌 + 一个用户对象」，放 `src/auth/session.ts` 读写
`localStorage` 即可，暂不引入 Pinia。学生 ID 从登录用户取，不再有跨页常量。

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
│   └── smoke-mock.mts         # mock 冒烟测试（30 项断言，含鉴权）
├── src/
│   ├── api/
│   │   ├── constants.ts       # 成功码 '0'、接口前缀
│   │   ├── types.ts           # Result / PageResult / SectionVO / ReviewTask / 状态与角色类型
│   │   ├── http.ts            # axios 实例、Bearer 注入、Result 解包、401 跳登录、ApiError
│   │   ├── auth.ts            # 登录 / 当前用户 / 登出
│   │   ├── sections.ts        # 板块接口：详情 / 保存 / 提交 / 撤回 / 待确认 / 明文
│   │   └── review.ts          # 审核接口：待审列表 / 通过 / 退回
│   ├── auth/
│   │   ├── session.ts         # 令牌与用户的读写、过期判定、角色判断
│   │   └── accounts.ts        # 演示账号清单（仅登录页一键填入用）
│   ├── components/
│   │   ├── FieldList.vue      # 按 fieldMeta 渲染字段（编辑态与只读态共用）
│   │   ├── PendingHint.vue    # 待确认区（旧值 → 新值）
│   │   └── DecisionDialog.vue # 审核通过 / 退回弹窗
│   ├── mocks/
│   │   ├── data.ts            # 内存数据 + 账号、权限与状态机模拟
│   │   ├── handlers.ts        # 登录与全部业务接口的 mock 实现
│   │   └── browser.ts         # Service Worker 启动
│   ├── router/index.ts        # 登录 + 六板块 + 审核两条路由 + 403 + 404，带登录与角色守卫
│   ├── views/
│   │   ├── LoginView.vue      # 登录页（账号密码 + 演示账号一键填入）
│   │   ├── ForbiddenView.vue  # 角色不足时的 403 页
│   │   ├── HomeView.vue       # 板块总览
│   │   ├── SectionShell.vue   # 板块页公共外壳（加载 / 表单 / 保存 / 提交 / 撤回）
│   │   ├── review/            # 待审列表与审核详情
│   │   ├── NotFoundView.vue   # 404
│   │   └── sections/          # 六个板块视图，各自独立文件
│   ├── fieldMeta.ts           # fieldMeta → 控件映射、表单值铺装、待确认条目类型
│   ├── sectionMeta.ts         # 板块名称、兜底字段、状态文案、演示参数、学年推算
│   ├── assets/main.css        # 全局样式与状态标签配色
│   ├── App.vue                # 布局、顶部导航、当前用户与退出登录、页脚
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
| `/login` | `login` | `LoginView.vue` | — | 登录页，`meta.public`，守卫放行 |
| `/` | `home` | `HomeView.vue` | — | 六板块入口卡片 |
| `/sections/basic` | `section-basic` | `sections/BasicSectionView.vue` | `basic` | 基本信息 |
| `/sections/academic` | `section-academic` | `sections/AcademicSectionView.vue` | `academic` | 学业发展 |
| `/sections/interest` | `section-interest` | `sections/InterestSectionView.vue` | `interest` | 兴趣特长 |
| `/sections/life` | `section-life` | `sections/LifeSectionView.vue` | `life` | 生活心理 |
| `/sections/career` | `section-career` | `sections/CareerSectionView.vue` | `career` | 职业规划 |
| `/sections/mentor` | `section-mentor` | `sections/MentorSectionView.vue` | `mentor` | 导师寄语 |
| `/review` | `review-list` | `review/ReviewListView.vue` | — | 审核工作台：待审 / 已通过 / 已退回，`meta.roles = [COU, ADMIN]` |
| `/review/:code/:studentId` | `review-detail` | `review/ReviewDetailView.vue` | — | 审核详情：只读字段、待确认值处理、通过 / 退回、该生六板块完成标记，同样限 `COU` / `ADMIN` |
| `/forbidden` | `forbidden` | `ForbiddenView.vue` | — | 角色不足时的落地页 |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundView.vue` | — | 404 |

路由按板块拆分组件（而不是一个通用页取参数），两人各自负责的板块落在不同文件，改动不冲突。
审核两条路由以板块编码为参数，六个板块共用一套页面。

### 登录与守卫

`router.beforeEach` 做两级判定，顺序不能颠倒：

1. **登录**：目标路由无 `meta.public` 且 `getToken()` 为空或已过期 → 跳 `/login?redirect=<原路径>`；
2. **角色**：目标路由有 `meta.roles` 且当前角色不在其中 → 跳 `/forbidden`（不静默跳首页，避免用户以为页面丢了）。

令牌存 `localStorage`（`growth.token` / `growth.user`），读出时留 30 秒余量判定过期，避免正好卡在边界。
`api/http.ts` 的请求拦截器统一注入 `Authorization: Bearer <token>`；响应 401 且不是登录接口时清会话并跳登录页，
其余错误照常抛 `ApiError` 交给页面展示。`redirect` 只接受站内路径，防开放跳转。

### 组件

| 组件 | 类型 | 作用 |
|---|---|---|
| `App.vue` | 布局 | 顶部导航（总览 + 审核工作台 + 六板块，按角色显隐）、当前用户与「退出登录」、`<RouterView>` 容器、页脚提示；登录页隐藏整套导航 |
| `LoginView.vue` | 页面 | 账号密码表单、演示账号一键填入、错误码就地提示、登录后回跳 `redirect` |
| `ForbiddenView.vue` | 页面 | 角色不足说明，给「回首页 / 换账号登录」两个出口 |
| `HomeView.vue` | 页面 | 六板块卡片，显示名称、一句话说明与接口路径 |
| `SectionShell.vue` | 板块外壳 | 学生端全部交互：加载详情、渲染表单、保存、提交、撤回、退回理由与待确认提示、字段级错误 |
| `sections/*View.vue` | 六个板块页 | 只把 `code` 传给 `SectionShell`；板块特有交互在此文件内扩展 |
| `FieldList.vue` | 字段渲染 | 按 `fieldMeta` 渲染控件；学生编辑态与审核只读态共用，敏感字段带「查看完整」 |
| `PendingHint.vue` | 待确认区 | 旧值 → 新值对比；学生只读，辅导员可确认 / 驳回 |
| `DecisionDialog.vue` | 弹窗 | 审核通过（意见可空）与退回（理由 ≥ 10 字），前端先校验一次 |
| `NotFoundView.vue` | 页面 | 404 |

### 表单渲染机制

页面**不硬编码表单字段**，字段清单由服务端 `fieldMeta` 下发（`arc_field_def` 是唯一真源）：

- 每个字段带 `key / fieldName / fieldType / required / options / sensitive / editable / pendingConfirm`。
- `fieldMeta.ts` 把 `fieldType` 映射为控件（TEXT→输入框、SELECT/RADIO→下拉、MULTI_SELECT→逗号分隔、FILE→暂不渲染上传），并按 `editable` 决定只读与提示文案。
- 字段值取自接口返回的 `fields`，保存时只提交 `editable=true` 且非 FILE 的字段，信封结构 `{ academicYear, fields }`。
- `sectionMeta.ts` 只作为接口未下发 `fieldMeta` 时的渲染兜底与模块导航数据。

### 数据流

```
SectionShell.vue / ReviewListView.vue / ReviewDetailView.vue
  → api/sections.ts、api/review.ts、api/completion.ts、api/auth.ts
  → api/http.ts（axios，baseURL=/api，注入 Authorization: Bearer）
  → Result 解包：code === '0' 返回 data；否则抛 ApiError(code / message / details / traceId)
  → 组件：成功刷新详情；失败按 code 展示提示，details 映射为字段下方红字
  → 401：清会话 + 跳 /login?redirect=<当前路径>（登录接口自身的 401 交给登录页展示）
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
| `npm run smoke:mock` | 用 `msw/node` 复跑 mock handlers，校验响应结构、权限与状态机（30 项断言） |

改动 `src/mocks/` 或 `src/api/types.ts` 后建议跑一次 `smoke:mock`，能挡住大部分契约漂移。

## 六、mock 与切换真实接口

默认 `VITE_USE_MOCK=true`，`main.ts` 在挂载前启动 Service Worker，`/api/**` 由 `mocks/handlers.ts` 接管，因此**不启动后端也能跑通全部页面**。

切换到真实后端：

1. 启动后端（见 [backend/README.md](../backend/README.md)），并按 `01 → 09` 跑完建库脚本（`sys_user` 若为空则无法登录）；
2. 本机新建 `.env.local`，写入 `VITE_USE_MOCK=false`；后端端口不是 8080 时再加 `VITE_BACKEND_ORIGIN=http://127.0.0.1:18080`；
3. 重启 `npm run dev`，用下面的演示账号登录（mock 与真实后端的账号清单一致）。

mock 覆盖：登录 / 当前用户 / 登出与未登录拦截，六板块详情 / 保存 / 提交、撤回（限一次）、待确认值确认与驳回、敏感字段明文、待审列表（按辅导员班级过滤）、通过 / 退回、完成标记读取与手动重算（含缺块「未建档」与整学年重算的权限差异），以及 `EC-40101`（未登录）、`EC-40104`（密码错）、`EC-40301`（越权字段）、`EC-40302`（数据范围）、`EC-42202`（必填缺失）、`EC-42203`（退回理由过短）、`EC-40902`（状态冲突）、`EC-40903`（撤回超限）等错误码。

完成标记（BR-S10）只在审核详情页展示，没有独立路由：卡片固定列六块，缺块显示「未建档」，
未完成清单直接由服务端下发的 `pendingSections` 渲染——前端不按 `status` 数组自己数，
否则「页面显示已完成、归档接口说没完成」这类不一致迟早会出现。完成标记读取失败只影响卡片本身，
不会把整个详情页打成错误态。

### 登录与演示账号

登录页用后端 `/auth/login` 换取令牌；mock 模式下 `mocks/handlers.ts` 用同一套账号数据模拟，令牌格式为
`mock.<userId>.<role>.<grant>`，业务处理器逐个校验，不带令牌一律 `EC-40101`。

| 登录名 | 角色 | 用途 |
|---|---|---|
| `2023010101` | 学生 | 填写、提交、撤回本人板块（`studentId=1001`） |
| `2023010102` / `2023010201` | 学生 | 另两个班的学生 |
| `9001` | 辅导员 | 软件 2401/2402 的待审与审核 |
| `9002` | 辅导员 | 电子 2401，用来验证跨班 403 与空列表 |
| `9003` | 管理员 | 持临时授权，只读查看 |
| `9004` | 管理员 | 无授权，验证 `EC-40302` |

密码统一 `Passw0rd!`，登录页提供一键填入。清单在 `src/auth/accounts.ts`，与后端
`09_insert_user_seed.sql` 保持一致；**演示账号只用于本地联调，上线前必须删除**。

## 七、注意事项

- **浏览器用 `http://localhost:5173`**。本机 Vite 可能只绑定 `::1`，用 `127.0.0.1` 会连接失败。
- **`node_modules/` 与 `dist/` 不入库**，已在根 `.gitignore` 忽略；不要为了「让同事直接跑」而提交它们。
- **不要硬编码表单字段**。新增字段只在 `sectionMeta.ts` 与后端 `SaveDTO` 两处加，页面自动渲染。
- **契约变更两边同步**。改字段名的顺序：`openapi.yaml` → 后端 `SaveDTO` → `sectionMeta.ts` → `mocks/data.ts`，最后跑 `smoke:mock`。
- **提交前至少跑 `npm run type-check`**；改了 mock 或类型再跑 `smoke:mock`。
- **登录态存 `localStorage`**，键为 `growth.token` / `growth.user`；学生 ID 从 `getUser().studentId` 取，
  不要再用常量。令牌过期由读出时的余量判定 + 401 拦截双保险，缺一不可。
- **身份只认令牌**。后端不再读 `X-User-*` 请求头，前端也不允许「传个头就换身份」的写法；
  需要换角色时走退出登录再登录。
- **错误展示分工**：`code` 与 `message` 直接展示，`traceId` 折叠展示供报障，`details` 按字段就位提示。
- **样式为骨架期手写**。引入 UI 组件库时先统一 `assets/main.css` 的变量，避免两套主题变量并存。

## 八、扩展指引

- **新增板块**：`router/index.ts` 加路由 → `views/sections/` 加视图（复用 `SectionShell`）→ `sectionMeta.ts` 加字段清单 → `mocks/handlers.ts` 与 `mocks/data.ts` 加数据。
- **板块特有交互**（附件上传、证书列表等）：写在对应 `sections/*View.vue` 内，公共外壳 `SectionShell.vue` 保持通用，避免影响其他板块。
- **新增接口**：`src/api/` 下按板块加函数，`handlers.ts` 补处理器，同步 `openapi.yaml`。
- **新增全局状态或登录**：先评估是否引入 Pinia；当前登录态已收敛在 `src/auth/session.ts`，
  令牌注入在 `api/http.ts` 的请求拦截器，**不要在组件里自己拼 `Authorization` 头**。
