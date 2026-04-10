# TikTok AI 智能选品系统

面向跨境电商卖家的 AI 选品平台。用户通过自然语言描述选品需求，系统自动规划数据获取、筛选、评分、排序的完整流程并执行，输出结果表格。

核心亮点：**Multi-Agent + MCP 架构** — 7 个专职 AI Agent 通过 MCP 协议协作，自动将自然语言转化为可执行的数据处理管道。

## Agent 团队

系统采用 Multi-Agent 架构，7 个 Agent 各司其职，覆盖从需求理解到执行后复盘的完整链路：

| Agent | 职责 | 触发方式 | 说明 |
|-------|------|---------|------|
| **main_agent** | 规划构建 | 用户输入 → `/intent/parse` | 核心 Agent。以 ReAct 循环（最多 30 轮）通过 MCP 协议调用 19 个 Tool，将自然语言需求逐步转化为可执行的积木链。支持多轮对话澄清、方案调整/回滚 |
| **memory_agent** | 记忆管理 | main_agent 同步调用 | 对话过程中读/写/检索结构化记忆，自动保存 MCP 工具调用结果，支持自然语言检索 |
| **interpret_agent** | 方案解读 | 积木链确认后 → `/intent/interpret-stream` | 将 block_chain JSON 翻译为用户可读的 Markdown 说明，SSE 流式输出，支持基于用户记忆的个性化表达 |
| **audit_agent** | 执行前审计 | 执行前同步调用 → `/intent/audit` | 规则 + LLM 混合审计积木链质量，返回通过/分数/问题列表/改进建议，拦截无意义或有风险的执行链 |
| **distillation_agent** | 记忆蒸馏 | 执行完成后异步触发 → `/intent/distillation` | 分析完整对话历史，提炼结构化记忆（用户偏好、选品参数摘要等），写回持久化存储供未来会话复用 |
| **competitor_agent** | 竞品洞察 | 执行完成后异步触发 → `/intent/competitor-stream` | 分析选品结果的竞争格局和市场机会，SSE 流式输出 Markdown 分析报告 |
| **compact_agent** | 历史压缩 | main_agent 内部自动触发 | 对话历史超过 18K 字符时自动激活，压缩为摘要 + 最近 8 条消息，节省 Token 开销 |

**协作流程：**

```
用户输入需求
  → main_agent (多轮 ReAct 构建积木链)
    ↔ memory_agent (跨会话记忆读写)
    → audit_agent (质量审计)
      → Block Engine 执行
        ├→ interpret_agent (方案解读, 流式)
        ├→ distillation_agent (记忆蒸馏, 异步)
        └→ competitor_agent (竞品洞察, 流式)
```

## 记忆系统

让 Agent 在多次会话中逐步了解用户偏好和决策模式，避免每次从零开始。Java 后端统一管理文件存储，Python 通过内部 HTTP 接口读写。

### 设计要点

- **存储**：文件系统（`/data/memory/{userId}/`）+ DB 索引（`user_memory_files` 表），NIO 文件锁保证多实例并发安全
- **跨进程互斥**：`FileChannel.tryLock()` 替代 JVM 内 ReentrantLock，进程崩溃时锁自动释放
- **MEMORY.md 索引**：每个 scope 一份索引文件，硬限制 200 条 / 25KB / 单条 ≤150 字符，超出截断
- **新鲜度提示**：>7 天记忆显示"X 天前记录"，>30 天追加"建议核实是否仍然适用"

### 记忆类型

| type | 说明 | 典型内容 | 存储位置 |
|------|------|---------|---------|
| `user` | 用户画像 | 常用市场、偏好品类、选品风格 | `common/` |
| `feedback` | 行为反馈 | 用户满意/不满的决策规律 | `common/` |
| `project` | 会话决策 | 本次会话的积木链思路、关键参数 | `select_product/{sessionId}/memory/` |
| `reference` | 外部资源 | 用户分享的参考链接、竞品信息 | `select_product/{sessionId}/memory/` |

### 读写时机

| 阶段 | 触发方 | 行为 |
|------|--------|------|
| 新会话开始 | main_agent | 加载 common 记忆索引；条目 >5 时智能筛选最相关 5 条；附新鲜度提示后注入 system prompt |
| ReAct 循环中 | main_agent → memory_agent | 关键决策可主动 `write_memory`；MCP 工具返回结果自动落盘 |
| 执行完成后 | Java 异步触发 distillation_agent | 提炼用户画像、反馈模式、`selection_summary`（强制写）等供下次复用 |
| 方案解读时 | interpret_agent | 读取 `type=user` 记忆生成个性化解读 |

### 目录结构

```
/data/memory/{userId}/
  common/                            # 跨会话长期记忆
    MEMORY.md                        # 索引
    user_profile.md                  # type: user
    feedback_preferences.md          # type: feedback
  select_product/{sessionId}/memory/ # 会话内短期记忆
    MEMORY.md
    decision_log.md                  # type: project
    reference_links.md               # type: reference
```

## 技术路线

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│  Vue 前端（3000）                                             │
│  对话页 / 执行详情页 / 管理后台 / 表格 (Univer)               │
└─────┬──────────────────────────────────────────────┬────────┘
      │ REST + SSE (JWT)                             │
      ↓                                              ↑
┌─────────────────────────────────────┐              │
│  Java 后端（8080）Spring Boot 3 + 21 │              │
│  ┌──────────────┐  ┌──────────────┐ │              │
│  │ REST 入口    │  │ MCP Server   │ │              │
│  │ Controllers  │  │ (JSON-RPC)   │ │              │
│  └──────┬───────┘  └──────▲───────┘ │              │
│         ↓ executeAsync    │ tools/* │              │
│  ┌──────────────────┐     │         │              │
│  │ BlockOrchestrator│     │         │  SSE 进度推送 │
│  │ runLoop()        │     │         │  step_complete│
│  │ ├ executeBlock() │     │         │  session_*    │
│  │ ├ Block Engine   │     │         │              │
│  │ │ ├ datasource/  │     │         │              │
│  │ │ ├ score/       │     │         │              │
│  │ │ ├ traverse/    │     │         │              │
│  │ │ └ enrichment/  │     │         │              │
│  │ └ SseEmitterMgr  │─────┼─────────┘              │
│  └──────────────────┘     │                        │
│  ┌──────────────────┐     │                        │
│  │ EchotikApiClient │     │                        │
│  │ KeyPoolManager   │     │                        │
│  └──────┬───────────┘     │                        │
└─────────┼─────────────────┼────────────────────────┘
          ↓ HTTPS           │
   ┌─────────────┐          │
   │ EchoTik API │          │
   └─────────────┘          │
                            │
              POST /intent/parse (HTTP)
                            │
                            ↓
┌─────────────────────────────────────┐
│  Python AI（8000）FastAPI            │
│  ┌──────────────────────────────┐   │
│  │ LangGraph ReAct StateGraph   │   │
│  │ fetch_tools→call_llm→tool    │   │
│  │ checkpoint: Redis (TTL 2h)   │   │
│  └──┬───────────────────────────┘   │
│     ↓                                │
│  ┌──────────────────────────────┐   │
│  │ 7 Agents                     │   │
│  │ main / memory / interpret    │   │
│  │ audit / distillation         │   │
│  │ competitor / compact         │   │
│  └──────────────────────────────┘   │
│  ┌──────────────────────────────┐   │
│  │ MCP Client (回调 Java MCP)   │───┘
│  └──────────────────────────────┘
└─────────────────────────────────────┘

底层：PostgreSQL 16（业务数据）+ Redis 7（checkpoint/SSE 缓冲/密钥池）
```

### Java 后端

**框架**：Spring Boot 3.2 + Java 21 + MyBatis-Plus + WebFlux（HTTP Client）

**核心：积木执行引擎**
- `BlockOrchestrator.runLoop()`：单线程顺序执行 block_chain，每步通过 `BlockExecutorRegistry` 动态查找 executor 实现，逐步累积 `LoopState`（输入数据/可用字段/API 调用统计）
- `BlockExecutor` 接口 + 4 类实现：`datasource/`（10 个数据源）、`score/`（数值/语义评分）、`traverse/`（实体跳转）、`enrichment/`（数据补充）
- `@McpBlock` / `@McpParam` 注解 + 反射自动生成 MCP Tool Schema，避免手写 JSON
- `BlockContext`/`BlockResult`/`LoopState` 三件套保证 block 间数据传递清晰

**核心：MCP Server**
- `McpController` + `McpDispatcher` 处理 JSON-RPC 2.0 三类请求：`tools/list`（动态可见性）、`tools/call`、`resources/read`
- `ChainBuildSession` 维护构建状态：blocks / availableFields / scoreFields / currentOutputType
- `FieldDictionary` 维护 blockId → 真实返回字段映射（102+ 字段全量对齐 EchoTik API）
- Pre/Post Hook 链：`QuotaPreHook`（配额校验）、`SensitiveCategoryPreHook`（敏感词检测）、`ResultStructurePostHook`（响应格式化）
- 幂等性：基于 `tool_call_id` 缓存避免重复执行

**SSE 进度推送**
- `SseEmitterManager` 维护 sessionId → SseEmitter 映射，缓冲先发布后订阅的事件（解决 race condition）
- 6 种事件：`step_start` / `step_complete` / `step_fail` / `session_complete` / `session_paused` / `session_fail`

**EchoTik API 管理**
- `EchotikApiClient`（WebFlux WebClient）+ Basic Auth
- `EchotikKeyPoolManager` Lua 脚本原子选最优密钥，Redis 缓存余量，防 TOCTOU 竞态

**安全**
- `JwtFilter` 用户 token 验证 + `McpAuthFilter` 内部接口 X-MCP-Token
- `AesEncryptUtil` 加密存储 EchoTik / LLM API 密钥

### Python AI

**框架**：FastAPI + LangChain + **LangGraph**（StateGraph 而非裸 ReAct）

**核心：main_agent ReAct 循环**
- StateGraph 三节点：`fetch_tools → call_llm → call_tools`，条件边控制循环退出
- `AgentState` TypedDict：`messages`（add_messages reducer）/ `iterations` / `total_tokens` / `chain_snapshot` / `written_memory_names`
- 流式 LLM 收集：每 100 字符推送 `thinking` 事件，15s keepalive 防 Java 端 timeout
- **三条恢复路径**：Redis checkpoint 续接（正常） / checkpoint 丢失从 qa_history 重建（降级） / 全新会话
- 最多 30 轮迭代，超 18K 字符触发 `compact_agent` 后台压缩

**Checkpoint 持久化**
- `AsyncRedisSaver`，TTL 2 小时，按 thread_id 复用
- 支持多轮续接，无需重传历史

**MCP Client**
- `mcp_client.py` 封装 JSON-RPC 调用 Java MCP Server
- `mcp_tools_to_openai_functions()` 自动转换 MCP Schema → OpenAI Function Calling 格式
- 每次 `/intent/parse` 创建独立实例，传入 `mcp_endpoint`

**LLM 抽象**
- `llm_factory.py` 单实例 + fallback 列表，主 LLM 失败自动切换备用
- 主 Agent 温度 0.2（探索性），蒸馏/压缩温度 0.1（稳定性）

**REST 端点**
| 端点 | Agent | 同/异步 | 响应 |
|------|-------|--------|------|
| `POST /intent/parse` | main_agent | 同步 | JSON：block_chain / action |
| `POST /intent/audit` | audit_agent | 同步 | JSON：{pass, score, issues, suggestions} |
| `POST /intent/interpret-stream` | interpret_agent | SSE | token 流 |
| `POST /intent/competitor-stream` | competitor_agent | SSE | token 流 |
| `POST /intent/distillation` | distillation_agent | 异步 202 | fire-and-forget |

### Vue 前端

**框架**：Vue 3 + TypeScript + Vite + `<script setup>` + Composition API

**状态管理：Pinia + Composables 混合**
- Pinia `stores/user.ts`：用户认证全局状态（token / role / tierName）
- Composable `useSession.ts`：会话详情 + 步骤 + 表格数据 + EventSource 生命周期
- Composable `useSessionHistory.ts`：双层缓存（内存 Map → IndexedDB → 服务器）
- Composable `useCrudTable.ts`：管理后台通用 CRUD

**SSE 集成**
- `EventSource` 监听 6 种事件，实时更新 `tableData` / 步骤状态 / 进度
- `watch(sessionId)` 自动切换连接，避免 leak

**Univer 表格**
- `UniverSheet.vue` 封装 `@univerjs/preset-sheets-core`
- 增量更新：SSE 推送时通过 `updateSheet()` 局部刷新而非全量重建
- 列格式化支持 `number / percent / score / string`

**HTTP 客户端**
- Axios + 拦截器：自动附加 JWT、统一业务码处理、401 自动登出、Blob 下载支持

**关键页面**
- `views/new-session.vue` — 选品对话主页（PlanningProgress + ChatMessage + SessionHistoryRail）
- `views/session-execution-detail.vue` — 执行详情（步骤面板 + Univer 表格）
- `views/admin/*` — 管理后台（用户/等级/API 密钥/预设/LLM 配置）

### 数据流：从用户输入到最终结果

```
1. 用户输入需求
   └─→ Vue: POST /api/intent/parse → Java IntentService

2. Java 异步转发 Python /intent/parse（携带 MCP 端点 + LLM 配置）
   └─→ Python LangGraph ReAct 启动
        ├─ memory_agent.query() 加载相关记忆
        ├─ fetch_tools(): MCP /tools/list → 19 个工具按可见性过滤
        ├─ call_llm(): 流式生成 tool_call
        ├─ call_tools(): MCP /tools/call → Java McpDispatcher
        │  └─ ChainBuildSession 累积 blocks 状态
        └─ 循环至 finalize_chain → 返回 block_chain

3. Java 收到 block_chain → 写入 Session 表 → 返回前端

4. 用户确认 → POST /api/sessions/{id}/execute
   └─→ BlockOrchestrator.executeAsync()
        ├─ Python audit_agent 审计
        ├─ runLoop() 逐步执行 block:
        │  ├─ datasource → EchotikApiClient → EchoTik API
        │  ├─ filter / score / sort 内存计算
        │  └─ traverse → 跨实体跳转
        ├─ 每步 SSE 推送 step_complete (rowCount, rows[], dims[])
        └─ 完成后 session_complete + 异步触发 distillation_agent

5. 前端 EventSource 实时接收 → useSession 更新 tableData
   └─ Univer 渲染表格 → 用户导出 Excel / 保存方案
```

### 部署

**Docker Compose 5 容器**：vue-frontend / java-backend / python-ai / postgres / redis

**关键配置**：
- `application.yml`：DB / Redis / MCP 内部 token / Python AI 地址 / Token 配额
- `python-ai/.env`：LLM API key / Java 内部 token / Redis 地址
- `vite.config.ts`：dev proxy → `http://java-backend:8080`

## 快速启动

```bash
git clone https://github.com/yangmingavada-cpu/tiktok-selection.git
cd tiktok-selection

# 配置环境变量
cp .env.example .env
# 编辑 .env，填入 DB_PASSWORD、LLM_API_KEY、ECHOTIK_API_BASE_URL 等

# 启动
docker compose up -d
```

| 服务 | 端口 |
|------|------|
| Vue 前端 | 3000 |
| Java 后端 | 8080 |
| Python AI | 8000 |
| PostgreSQL | 5432 |
| Redis | 6379 |

## 初始配置（首次启动必做）

启动后需要用管理员账号进入后台完成配置，否则 AI 选品功能无法使用。

**默认管理员账号：**
- 邮箱：`admin@tiktok-selection.com`
- 密码：`123456`

> 如果你修改过数据库初始化脚本，请以实际配置为准。

**后台配置步骤：**

1. 登录后进入管理后台（`/admin`）
2. **LLM 配置** — 添加 LLM 接口（需兼容 OpenAI 格式），填入 API 地址和密钥
3. **EchoTik API 密钥** — 添加 EchoTik 数据平台的 API Key/Secret
4. **同步类目数据** — 点击"同步类目"拉取商品分类树（AI 选品时需要）

完成以上配置后，用户端即可正常使用 AI 选品功能。

## 使用流程

1. 访问 `http://localhost:3000`，注册登录
2. 新建选品 → 用自然语言描述需求（如"找泰国月销>5000的美妆品"）
3. AI 自动规划积木链 → 方案解读 → 确认执行
4. 实时查看执行进度和结果表格
5. 导出 Excel 或保存为方案模板

## 项目结构

```
tiktok-selection/
├── java-backend/          # Spring Boot 后端
│   └── src/.../selection/
│       ├── engine/        # 积木链编排引擎
│       ├── mcp/           # MCP Server + 21 个 Tool
│       ├── service/       # 业务服务层
│       └── controller/    # REST API
├── python-ai/             # Python AI 服务
│   └── app/
│       ├── agents/        # 6 个 Agent
│       ├── routers/       # FastAPI 路由
│       └── services/      # MCP/LLM/Memory 客户端
├── vue-frontend/          # Vue 3 前端
├── sql/                   # 数据库初始化脚本
└── docker-compose.yml
```
