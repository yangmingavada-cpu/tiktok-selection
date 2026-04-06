# TikTok AI 智能选品系统

面向跨境电商卖家的 AI 选品平台。用户通过自然语言描述选品需求，系统自动规划数据获取、筛选、评分、排序的完整流程并执行，输出结果表格。

核心亮点：**Multi-Agent + MCP 架构** — 6 个专职 AI Agent 通过 MCP 协议协作，自动将自然语言转化为可执行的数据处理管道。

## Agent 团队

系统采用 Multi-Agent 架构，6 个 Agent 各司其职，覆盖从需求理解到执行后复盘的完整链路：

| Agent | 职责 | 触发方式 | 说明 |
|-------|------|---------|------|
| **main_agent** | 规划构建 | 用户输入 → `/intent/parse` | 核心 Agent。以 ReAct 循环（最多 15 轮）通过 MCP 协议调用 21 个 Tool，将自然语言需求逐步转化为可执行的积木链。支持多轮对话澄清、方案调整/回滚 |
| **interpret_agent** | 方案解读 | 积木链确认后 → `/intent/interpret-stream` | 将 block_chain JSON 翻译为用户可读的 Markdown 说明，SSE 流式输出，支持基于用户记忆的个性化表达 |
| **audit_agent** | 执行前审计 | 执行前同步调用 → `/intent/audit` | 规则 + LLM 混合审计积木链质量，返回通过/分数/问题列表/改进建议，拦截无意义或有风险的执行链 |
| **distillation_agent** | 记忆蒸馏 | 执行完成后异步触发 → `/intent/distillation` | 分析完整对话历史，提炼结构化记忆（用户偏好、选品参数摘要等），写回持久化存储供未来会话复用 |
| **competitor_agent** | 竞品洞察 | 执行完成后异步触发 → `/intent/competitor-stream` | 分析选品结果的竞争格局和市场机会，SSE 流式输出 Markdown 分析报告 |
| **compact_agent** | 历史压缩 | main_agent 内部自动触发 | 对话历史超过 18K 字符时自动激活，压缩为摘要 + 最近 8 条消息，节省 Token 开销 |

**协作流程：**

```
用户输入需求
  → main_agent (多轮 ReAct 构建积木链)
    → audit_agent (质量审计)
      → Block Engine 执行
        ├→ interpret_agent (方案解读, 流式)
        ├→ distillation_agent (记忆蒸馏, 异步)
        └→ competitor_agent (竞品洞察, 流式)
```

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + Univer |
| 后端 | Spring Boot 3.2 + Java 21 + MyBatis-Plus |
| AI | FastAPI + LangChain + LangGraph + MCP |
| 数据 | PostgreSQL 16 + Redis 7 |
| 部署 | Docker Compose（5 容器） |

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
