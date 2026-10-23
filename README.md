# AI 角色酒馆

一个基于大模型的 AI 角色扮演与叙事创作平台：用户创建 / 挑选角色卡，与 AI 角色进行**流式多轮对话**，再把对话记录**改写成小说或剧本**并发布到社区；平台配套管理端与**MCP 服务**，把系统能力开放给 AI 客户端调用。

> 单体 Spring Boot + 双 Vue 3 前端 + 独立 Node.js MCP 服务，个人独立开发。

## 系统规模

| 模块 | 说明 | 规模 |
|---|---|---|
| `ai-tavern/` | Spring Boot 后端（8080） | 112 个 Java 文件 / 6100+ 行（另 4 个测试类 474 行）· 22 张表 · 90 个 REST 接口 · 38 个单元测试 |
| `ai-tavern-web/` | 用户端 Vue 3（5173） | 44 个源文件 / 8600+ 行 + 77 个 shadcn-vue 组件 |
| `ai-tavern-admin/` | 管理端 Vue 3（5174） | 12 个组件 / 1400+ 行 |
| `ai-tavern-mcp/` | MCP 服务（stdio，独立 Node 进程） | 9 个工具 · 350+ 行；另含自研宿主 `agent.mjs` |
| `sql/` | 建表与演示数据 | `schema.sql` / `seed.sql` |
| `scripts/` | SillyTavern PNG 角色卡批量导入 | `import-st-cards.mjs` |

## 技术栈

- **后端**：JDK 17、Spring Boot 3.2.5、MyBatis、MySQL 8、Redis、OkHttp、jjwt、BCrypt、阿里云 OSS
- **前端**：Vue 3.5、Vue Router、Pinia、Tailwind CSS 4、shadcn-vue（组件源码入库，可改可删）、reka-ui、lucide 图标、vue-sonner、marked、ECharts 6（按需引入）、Vite 8
- **MCP 服务**：Node.js ESM、`@modelcontextprotocol/sdk`（stdio 传输）
- **AI**：OpenAI 兼容协议（`/chat/completions`），支持流式与多模型热切换

## 关键实现

1. **手写 SSE 流式对话链路**：后端 OkHttp 逐行解析 `data:` 事件与 `[DONE]` 终止、取 `choices[0].delta.content`，再用 `SseEmitter`（180s 超时，连接 / 读 / 写超时分级 30s / 180s / 60s）转发给前端；前端不用 `EventSource`，而是 `fetch` + `response.body.getReader()` + `TextDecoder` 手写分块解析与增量渲染。
2. **Token 计量与积分计费**：优先读取响应 `usage.total_tokens`，缺失时按内容长度兜底估算；积分按每千 token 向上取整；`@Transactional` 保证「扣减余额 + 写流水 + 会话累计」的原子性，流水记录扣费前后余额，余额不足按剩余额度扣减并正常返回。
3. **MCP 能力开放层**：独立 Node 进程以 stdio 暴露 **9 个工具**（角色卡生成、立绘生成、草稿保存、SillyTavern V2 导出、世界树生成、节点扩展、对话改写为叙事、会话查询、作品发布），经 HTTP + Bearer JWT 转调后端 REST；工具调用统一计时并回传 `/api/metrics/mcp/report`，由前端渲染调用时间线。
4. **面向 LLM 输出不确定性的工程护栏**：结构化产物（角色卡 / 世界树）采用 JSON 契约约束 + markdown 围栏剥离 + 字段级兜底降级 + 递归编号；模型名 / base_url / 温度 / top_p / max_tokens / 上下文窗口入库，支持不重启热切换，密钥读取时脱敏，未配置 API Key 时降级为本地模拟流式。
5. **上下文治理与叙事生成**：对话记录清洗（剔除 system 消息、连续重复轮次、无效短消息）后按 3 文体 × 4 风格 × 3 篇幅模板组装分层 Prompt，按 **4000 token 预算**分层裁剪（保留首尾、中间等间隔抽样、超限补剧情线索），流式生成并支持 TXT / Markdown 导出。
6. **中文内容审核引擎**：全角→半角、去零宽字符与空白、繁转简三阶归一化后，用**允许插入 0–2 个无关字的间隔容错匹配**拦截拆字 / 谐音等绕过手段；词库以 Redis Set 缓存 + 版本号驱动匹配结构重建；输入侧命中即拒不写库，输出侧落库前截断回退并记违规日志。
7. **缓存与运行机制可视化**：热门角色 / 标签 / 模型配置分级 TTL 缓存与写操作主动失效；Redis ZSet 维护点赞收藏热榜；固定窗口频控（评论 3 次/分、点赞 20 次/分）与 `SET NX` + 唯一索引双层去重；前端 ECharts 展示上下文占用条、Token 明细、缓存命中率前后对比与 MCP 调用时间线。
8. **前端设计系统与流式交互**：Tailwind v4 的 CSS 变量主题 + shadcn-vue（组件源码进仓，可直接改），改一处令牌即全站换肤；对话页自写 markdown 渲染（解析后丢弃原始 HTML 防注入）、流式打字光标、仅在贴底时自动跟随、可中断生成并保留已收内容；ECharts 按需注册，可视化页 chunk 从 1.14 MB 降到 0.55 MB，用户端总产物从 1.87 MB 降到 1.29 MB。

## 快速开始

### 1. 准备环境变量

仓库**不含任何密钥**。真实凭据放在被 git 忽略的 `ai-tavern/src/main/resources/application-local.yml`（可参考字段自行创建），或直接用环境变量：

```bash
# 数据库 / Redis（不设则用 127.0.0.1 与空密码）
DB_USERNAME=root
DB_PASSWORD=你的密码

# 阿里云 OSS（不配则图片上传相关功能不可用）
OSS_ACCESS_KEY_ID=
OSS_ACCESS_KEY_SECRET=
OSS_PUBLIC_BASE_URL=

# 大模型（不配则自动降级为本地模拟流式，仍可完整联调）
AI_API_KEY=
AI_BASE_URL=https://api.deepseek.com/v1

# JWT 签名密钥（生产必须覆盖）
JWT_SECRET=
```

### 2. 初始化数据库

```bash
mysql -uroot -p < sql/schema.sql     # 建库建表 + 基础分类/标签/敏感词
mysql -uroot -p < sql/seed.sql       # 可选：演示角色「林晚晴」、管理员密码重置
```

`sql/schema.sql` 与 `ai-tavern/src/main/resources/schema.sql` 内容保持一致，改表时两边同步。

### 3. 启动三个进程

```bash
# 后端
cd ai-tavern && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 用户端
cd ai-tavern-web && npm install && npm run dev

# 管理端
cd ai-tavern-admin && npm install && npm run dev
```

### 4. 接入 MCP（可选）

`ai-tavern-mcp/` 是一个标准 MCP 服务，任何支持 MCP 的客户端都能接；README 见 `ai-tavern-mcp/README.md`。

## 默认账号

- 管理员：`admin` / `admin123`（管理端 5174，仅演示用，部署前请修改）
- 普通用户：自行注册（用户端 5173）

## 目录结构

```
ai-tavern/           Spring Boot 后端（controller / service / mapper / entity）
ai-tavern-web/       用户端 Vue 3
  src/components/ui/   shadcn-vue 组件（源码入库，可随意改）
  src/components/      业务组件（角色卡、会话列表、充值弹窗等）
ai-tavern-admin/     管理端 Vue 3
ai-tavern-mcp/       MCP 服务（server.mjs）+ 自研宿主（agent.mjs）
sql/                 schema.sql + seed.sql
scripts/             SillyTavern 角色卡批量导入脚本
```

## 安全说明

- 仓库内**不含**任何数据库口令、云存储密钥、大模型 Key 或 JWT 密钥，全部通过环境变量或未跟踪的 `application-local.yml` 注入。
- 若你在本地配过真实凭据，请确认 `application-local.yml` 未被 `git add`（已在 `.gitignore` 中）。
