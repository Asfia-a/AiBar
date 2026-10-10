# AI 酒馆 MCP 服务 + 自研宿主

把「AI 角色酒馆」的能力开放给任意 AI 客户端：生成角色卡 / 立绘 / 世界树、把对话改写成小说剧本、发布作品。

目录里有两个可执行文件：

| 文件 | 角色 | 说明 |
|---|---|---|
| `server.mjs` | **MCP 服务端** | 声明并实现 9 个工具，stdio 传输，通过 HTTP + Bearer JWT 转调酒馆后端 |
| `agent.mjs` | **自研宿主（Agent）** | MCP 客户端 + function calling 循环：自己就能把工具跑起来，不依赖第三方客户端 |

> 为什么要自己写宿主：宿主只依赖 MCP 协议，不依赖某个具体产品。自己实现一遍既能验证协议实现是否正确，也让整条链路可以自主演示；换来的是"协议层与业务层解耦，宿主可替换"。

## 安装

```bash
cd ai-tavern-mcp
npm install
```

## 工具（9 个）

| 工具 | 作用 | 是否需要后端 |
|---|---|---|
| `generate_character_card` | 按想法生成角色卡草稿，可选同时生成立绘 | 是 |
| `generate_character_avatar` | 生成角色立绘并上传 OSS，返回公网 URL | 是 |
| `save_character_draft` | 生成角色卡并保存为当前用户的草稿角色 | 是 |
| `export_st_card_json` | **纯本地**：把角色字段打包成 SillyTavern `chara_card_v2` JSON | 否 |
| `generate_world_tree` | 按自然语言生成「世界树」层级世界观（可指定语言与深度） | 是 |
| `expand_world_tree_node` | 为世界树某个节点生成子节点 | 是 |
| `rewrite_chat_as_narrative` | 把一场对话改写成小说 / 剧本 / 大纲并落库（会扣积分） | 是 |
| `list_narrative_sessions` | 列出可改写的会话 | 是 |
| `publish_narrative_work` | 把叙事作品发布到社区广场 | 是 |

**工具划分原则**：需要业务数据或模型能力 → 转调后端；纯数据格式转换 → 本地做（`export_st_card_json` 因此不依赖后端，也不需要登录态）。

所有需要后端的调用都会被 `timed()` 统一计时，并把耗时与成败回报到后端 `/metrics/mcp/report`，供前端渲染「MCP 调用时间线」。

## 用法一：接第三方宿主（Cursor 等）

```json
{
  "mcpServers": {
    "ai-tavern": {
      "command": "node",
      "args": ["E:/Myproject/AiBar/ai-tavern-mcp/server.mjs"],
      "env": {
        "TAVERN_API_BASE": "http://127.0.0.1:8080/api",
        "TAVERN_JWT": "粘贴登录 token"
      }
    }
  }
}
```

浏览器登录后，在 Local Storage → `tavern_token` 复制即可。改完 MCP 后需在宿主里重启该服务。

## 用法二：跑自研宿主 `agent.mjs`

```bash
# 1) 只列工具（不需要模型 Key，也不需要后端）
node agent.mjs --list-tools

# 2) 离线自检：连服务 + 列工具 + 真调一次纯本地工具 export_st_card_json
node agent.mjs --selftest

# 3) 真正跑 Agent（需要模型 Key 和后端）
set AI_API_KEY=sk-xxx
set TAVERN_JWT=粘贴登录 token
node agent.mjs "帮我设计一个赛博朋克风格的角色，并导出 SillyTavern 卡"
node agent.mjs --max-rounds 6 "先列一下有哪些会话可以改写成小说"
```

环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `AI_API_KEY` | 空 | 模型 Key；**不设则自动进入离线自检** |
| `AI_BASE_URL` | `https://api.deepseek.com/v1` | OpenAI 兼容接口地址 |
| `AI_MODEL_NAME` | `deepseek-chat` | 模型名 |
| `TAVERN_API_BASE` | `http://127.0.0.1:8080/api` | 透传给 MCP 服务的后端地址 |
| `TAVERN_JWT` | 空 | 透传给 MCP 服务的登录态 |
| `MCP_SERVER` | 同目录 `server.mjs` | 换成别的 MCP 服务脚本也能连 |

### 它是怎么工作的

1. 用 `StdioClientTransport` 把 MCP 服务当子进程拉起，`Client` 建立连接；
2. `tools/list` 拿到工具声明，**直接把 MCP 的 `inputSchema` 转成 OpenAI 的 `tools[].function.parameters`**（两者几乎 1:1）；
3. 循环：把对话 + tools 发给 `/chat/completions` → 模型若返回 `tool_calls`，就逐个经 MCP 的 `tools/call` 执行 → 把结果以 `role: "tool"` 回灌 → 进入下一轮；
4. 模型不再调用工具时，输出最终答复。默认最多 8 轮（`--max-rounds` 可调）。

两个容易踩的点，代码里都处理了：

- **带 `tool_calls` 的 assistant 消息必须原样回灌**，否则下一轮模型不知道自己在等工具结果；
- **`arguments` 是字符串不是对象**，要 `JSON.parse`，解析失败不能抛异常，要把错误当成工具结果回传给模型让它重试。

### 世界树示例

```js
generate_world_tree({ "idea": "永夜港口，真名会留下银色足迹", "language": "中文", "depth": 3 })
```

返回的 `tree` 可写入前端 `localStorage["tavern_world_tree"]`，刷新「世界树」页即可看到。

## 已知局限

- 服务端只实现了 `tools` 这一类能力，没有 `resources` / `prompts` / `sampling`（`sampling` 能让服务端反向请求宿主跑模型，是 MCP 里很有意思的一块，尚未实现）；
- 登录态靠手工粘贴 token，过期需重粘，没有做设备码 / OAuth；
- 自研宿主的 function calling 循环是针对 OpenAI 兼容接口写的，没有做多模型适配层。
