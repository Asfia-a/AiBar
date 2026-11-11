# AI 角色酒馆

基于大模型的角色扮演与叙事创作平台：角色卡 / 世界树、流式多轮对话，再改写成小说或剧本并发布。配套管理端与 MCP，把能力开放给 AI 客户端。

单体 Spring Boot + 双 Vue 3 + 独立 Node.js MCP。当前分支在基线之上做了界面重写、提示词引擎与世界树广场。

![用户端首页](docs/screenshots/01-home.jpg)

| 管理端概览 | 模型配置 |
|---|---|
| ![管理端](docs/screenshots/02-admin-dashboard.jpg) | ![模型配置](docs/screenshots/04-admin-model.jpg) |

## 功能概览

- **流式对话**：SSE 全链路；打字光标、贴底跟随、可中断并保留已生成内容
- **消息多版本**：助手回复可滑动切换 / 重生，旧版保留；可编辑、可分支出新会话
- **提示词引擎**：对标 SillyTavern——世界书关键词 / 正则激活、作者注释、token 预算裁剪；支持命中预演
- **世界树**：创作者编辑视图 + 广场探索（区域、时间线、漫游遭遇）；支持完整世界观导入
- **积分计费**：按 token 扣费，与滑动重生共用同一套落库逻辑
- **MCP**：9 个工具（角色 / 世界树 / 叙事 / 发布等）+ 可选自研宿主 `agent.mjs`
- **审核与缓存**：中文间隔容错审核；热门 / 配置缓存与频控；运行指标可视化
- **前端**：Tailwind v4 主题令牌 + 源码入库的 shadcn-vue；亮 / 暗主题

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | JDK 17 · Spring Boot 3.2 · MyBatis · MySQL 8 · Redis · OkHttp · JWT |
| 前端 | Vue 3 · Pinia · Tailwind CSS 4 · shadcn-vue · reka-ui · ECharts 6（按需）· Vite 8 |
| MCP | Node.js · `@modelcontextprotocol/sdk`（stdio） |
| AI | OpenAI 兼容协议；采样参数可热切换（非标准参数对本机推理按需下发） |

## 仓库结构

```
ai-tavern/             Spring Boot 后端（8080）
ai-tavern-web/         用户端（5173）
  src/components/ui/     shadcn-vue 组件源码
ai-tavern-admin/       管理端（5174）
ai-tavern-mcp/         MCP server + agent 宿主
sql/                   schema · seed · migrations
scripts/               导入与联调脚本
```

## 快速开始

**1. 环境变量**（仓库不含密钥）

```bash
DB_USERNAME=root
DB_PASSWORD=你的密码
AI_API_KEY=                  # 不配则本地模拟流式
AI_BASE_URL=https://api.deepseek.com/v1
JWT_SECRET=
```

**2. 数据库**

```bash
mysql -uroot -p < sql/schema.sql
mysql -uroot -p < sql/seed.sql           # 可选
# 需要增量能力时再执行 sql/migration_*.sql
```

**3. 启动**

```bash
# PowerShell 下推荐用环境变量，避免 -D 参数被拆开
$env:SPRING_PROFILES_ACTIVE='local'
cd ai-tavern; mvn spring-boot:run

cd ai-tavern-web; npm install; npm run dev
cd ai-tavern-admin; npm install; npm run dev
```

MCP 见 [`ai-tavern-mcp/README.md`](ai-tavern-mcp/README.md)。

## 默认账号

- 管理端 `5174`：`admin` / `admin123`（演示用，部署前请改）
- 用户端 `5173`：自行注册

## 规模（约）

| 模块 | 说明 |
|---|---|
| 后端 | ~125 Java 文件 / 7500+ 行 · 23 表 · 90+ REST · 74 单测 |
| 用户端 | ~44 源文件 / 8600+ 行 + shadcn-vue 组件 |
| 管理端 | ~12 组件 / 1400+ 行 |
| MCP | 9 工具 + `agent.mjs` |
| 构建 | 用户端产物约 1.3 MB（可视化 chunk ~0.55 MB） |

## 安全

凭据只通过环境变量或未跟踪的 `application-local.yml` 注入；请勿提交本地密钥文件。
