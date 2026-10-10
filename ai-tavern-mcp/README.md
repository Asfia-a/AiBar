# AI 酒馆 MCP（创意工坊 + 世界树）

让 Cursor Agent 调用酒馆后端：生成角色卡 / 立绘 / 世界树，并导出 SillyTavern JSON。

## 安装

```bash
cd ai-tavern-mcp
npm install
```

## 配置 Cursor

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

浏览器登录后，在 Local Storage → `tavern_token` 复制即可。改完 MCP 后需在 Cursor 里重启该 MCP。

## 工具

| 工具 | 作用 |
|------|------|
| `generate_character_card` | AI 写人设，可选生图 |
| `generate_character_avatar` | 仅立绘 → OSS |
| `save_character_draft` | 生成并保存草稿角色 |
| `export_st_card_json` | 打包 ST V2 JSON |
| `generate_world_tree` | 按用户语言一键生成世界树 |
| `expand_world_tree_node` | 为节点扩展子节点 |

### 世界树示例

```
generate_world_tree({
  "idea": "永夜港口，真名会留下银色足迹",
  "language": "中文",
  "depth": 3
})
```

返回的 `tree` 可写入前端 `localStorage["tavern_world_tree"]`，刷新「世界树」页即可看到。网页内也有「一键生成世界树」按钮。
