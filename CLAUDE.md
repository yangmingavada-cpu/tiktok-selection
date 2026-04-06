# CLAUDE.md - 项目指引

## 项目概述
TikTok跨境电商智能选品系统，包含三个服务：
- **java-backend**: Spring Boot 后端 (端口 8080)
- **python-ai**: FastAPI AI服务 (端口 8000)
- **vue-frontend**: Vue 3 前端 (端口 3000)
- **PostgreSQL** + **Redis** 数据存储

## 运行环境
- Docker Compose 部署，容器名：tiktok-java, tiktok-python, tiktok-frontend, tiktok-postgres, tiktok-redis
- 数据库：tiktok_selection，用户：tiktok_app
- Python 服务挂载源码目录，支持热重载

## Git 规范

### 仓库信息
- 远程仓库：https://github.com/yangmingavada-cpu/tiktok-selection.git
- 主分支：main
- 仓库类型：Private

### 提交规范
每次修改代码后必须提交到 GitHub，使用以下流程：

```bash
git add <修改的文件>
git commit -m "<type>: <简要描述>"
git push
```

#### Commit Message 格式
- `feat: 新增XX功能` — 新功能
- `fix: 修复XX问题` — Bug修复
- `refactor: 重构XX模块` — 代码重构
- `docs: 更新XX文档` — 文档变更
- `chore: 更新依赖/配置` — 构建/工具变更

#### 提交原则
1. **每完成一个独立功能或修复就提交**，不要攒一大堆改动
2. **提交前检查**：`git status` 确认文件列表，`git diff` 确认改动内容
3. **不要提交敏感文件**：`.env`、密钥、凭证等（已在 .gitignore 中排除）
4. **提交信息用中文**，简明扼要说明改了什么、为什么改

### .gitignore 已排除
- `.env` / `.env.local` — 含密钥
- `java-backend/target/` — 编译产物
- `node_modules/` / `dist/` — 前端依赖和构建产物
- `__pycache__/` / `*.pyc` — Python缓存
- `.idea/` / `.vscode/` — IDE配置
