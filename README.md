# TikTok AI 智能选品系统

面向跨境电商卖家的 AI 选品平台。用户通过自然语言描述选品需求，系统自动规划数据获取、筛选、评分、排序的完整流程并执行，输出结果表格。

核心亮点：**Multi-Agent + MCP 架构** — 6 个专职 AI Agent 通过 MCP 协议协作，自动将自然语言转化为可执行的数据处理管道。

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
