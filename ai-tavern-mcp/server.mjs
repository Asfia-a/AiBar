#!/usr/bin/env node
/**
 * Cursor MCP：创意工坊 + 世界树
 *
 * 环境变量：
 *   TAVERN_API_BASE   默认 http://127.0.0.1:8080/api
 *   TAVERN_JWT        登录后的 Bearer token
 */
import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js'

const API_BASE = (process.env.TAVERN_API_BASE || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const JWT = process.env.TAVERN_JWT || ''

/**
 * 调后端接口。默认 POST，list 开头的查询类走 GET。
 * 后端统一返回 { code, message, data }，code 非 0/200 一律当失败抛。
 */
async function api(path, body, { method } = {}) {
  if (!JWT) {
    throw new Error('未设置 TAVERN_JWT：请先登录酒馆，把 token 配进 MCP env')
  }
  const isGet = method === 'GET' || path.startsWith('/list')
  const res = await fetch(`${API_BASE}${path}`, {
    method: isGet ? 'GET' : 'POST',
    headers: {
      Authorization: `Bearer ${JWT}`,
      ...(isGet ? {} : { 'Content-Type': 'application/json' }),
    },
    ...(isGet ? {} : { body: JSON.stringify(body || {}) }),
  })
  const json = await res.json()
  if (!res.ok || (json.code !== 0 && json.code !== 200)) {
    throw new Error(json.message || `HTTP ${res.status}`)
  }
  return json.data
}

/**
 * 上报一次工具调用，供「运行机制可视化」的 MCP 时间线用。
 * 埋点失败绝不能影响主流程，所以整段吞掉异常。
 */
async function report(tool, cost, success) {
  try {
    await fetch(`${API_BASE}/metrics/mcp/report`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${JWT}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ tool, cost, success }),
    })
  } catch {
    /* 埋点失败忽略 */
  }
}

/** 包一层，统一计时并回报埋点 */
async function timed(tool, fn) {
  const start = Date.now()
  try {
    const data = await fn()
    await report(tool, Date.now() - start, true)
    return data
  } catch (e) {
    await report(tool, Date.now() - start, false)
    throw e
  }
}

const server = new Server(
  { name: 'ai-tavern-mcp', version: '1.1.0' },
  { capabilities: { tools: {} } },
)

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: 'generate_character_card',
      description:
        '用 AI 生成 SillyTavern 兼容角色卡草稿（人设/开场白等），可选生图上传 OSS。返回可直接使用的字段与 stCard JSON。',
      inputSchema: {
        type: 'object',
        properties: {
          idea: { type: 'string', description: '角色想法描述' },
          worldContext: { type: 'string', description: '可选世界观/灵感上下文' },
          withAvatar: { type: 'boolean', description: '是否同时生成立绘，默认 true' },
        },
        required: ['idea'],
      },
    },
    {
      name: 'generate_character_avatar',
      description: '根据提示词生成角色立绘并上传到 OSS，返回公网 URL',
      inputSchema: {
        type: 'object',
        properties: {
          prompt: { type: 'string', description: '立绘提示词（建议英文）' },
          name: { type: 'string', description: '角色名，拼进提示词' },
        },
        required: ['prompt'],
      },
    },
    {
      name: 'save_character_draft',
      description: '根据想法生成角色卡并保存为当前用户的草稿角色（我的角色）',
      inputSchema: {
        type: 'object',
        properties: {
          idea: { type: 'string' },
          worldContext: { type: 'string' },
          withAvatar: { type: 'boolean' },
        },
        required: ['idea'],
      },
    },
    {
      name: 'export_st_card_json',
      description: '把角色字段打包成 SillyTavern chara_card_v2 JSON 文本，便于导出文件',
      inputSchema: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          description: { type: 'string' },
          personality: { type: 'string' },
          backgroundStory: { type: 'string' },
          greeting: { type: 'string' },
          exampleDialogue: { type: 'string' },
          tags: { type: 'array', items: { type: 'string' } },
          avatar: { type: 'string' },
        },
        required: ['name'],
      },
    },
    {
      name: 'generate_world_tree',
      description:
        '根据用户自然语言一键生成「世界树」层级世界观（世界→时代→地域/势力/法则/人物线索）。输出语言默认跟随用户想法所用语言，也可指定 language。返回 tree JSON 与 contextText 摘要，可直接写入前端 localStorage 键 tavern_world_tree。',
      inputSchema: {
        type: 'object',
        properties: {
          idea: {
            type: 'string',
            description: '世界观想法，任意语言（中文/English/日本語等）',
          },
          language: {
            type: 'string',
            description: '可选输出语言，如 中文 / English / 日本語；空则跟随想法语言',
          },
          depth: {
            type: 'number',
            description: '树深度提示 2～4，默认 3',
          },
        },
        required: ['idea'],
      },
    },
    {
      name: 'expand_world_tree_node',
      description: '为世界树某个已有节点 AI 生成子节点（地域/势力/法则/人物线索等）',
      inputSchema: {
        type: 'object',
        properties: {
          node: {
            type: 'object',
            description: '父节点 { label, type, summary }',
            properties: {
              label: { type: 'string' },
              type: { type: 'string' },
              summary: { type: 'string' },
            },
          },
          hint: { type: 'string', description: '扩展方向提示' },
          language: { type: 'string', description: '输出语言，可选' },
        },
        required: ['node'],
      },
    },
    {
      name: 'rewrite_chat_as_narrative',
      description:
        '把一场已有的角色扮演对话改写成小说/剧本/大纲并落库为叙事作品。走同步接口（等生成完一次性返回），会真实扣除积分。适合在 Cursor 里批量把聊天记录转成可发布的作品。',
      inputSchema: {
        type: 'object',
        properties: {
          sessionId: { type: 'number', description: '来源会话 ID，可先用 list_narrative_sessions 查' },
          formType: {
            type: 'string',
            description: '文体：novel 小说 / script 剧本 / outline 大纲，默认 novel',
          },
          style: {
            type: 'string',
            description: '风格：literary 文学 / colloquial 口语 / poetic 诗意，默认 literary',
          },
          length: {
            type: 'string',
            description: '篇幅：short 短篇 / medium 中篇 / long 长篇，默认 medium',
          },
          extraInstruction: { type: 'string', description: '额外要求，如"第一人称、结尾留白"' },
        },
        required: ['sessionId'],
      },
    },
    {
      name: 'list_narrative_sessions',
      description: '列出可作为改写来源的会话（至少 4 条消息），配合 rewrite_chat_as_narrative 使用',
      inputSchema: {
        type: 'object',
        properties: {},
      },
    },
    {
      name: 'publish_narrative_work',
      description: '把一篇叙事作品发布到社区广场。发布前会过一遍内容审核，未通过会返回原因且作品仅作者可见。',
      inputSchema: {
        type: 'object',
        properties: {
          workId: { type: 'number', description: '作品 ID' },
        },
        required: ['workId'],
      },
    },
  ],
}))

/**
 * 工具路由表。每个工具都是「调后端接口 + 包装成 MCP 返回值」，
 * 外面统一套 timed() 计时并回报埋点，所以新增工具只需在这里加一条。
 */
const handlers = {
  generate_character_card: (args) =>
    timed('generate_character_card', () =>
      api('/workshop/character/generate', {
        idea: args.idea,
        worldContext: args.worldContext || '',
        withAvatar: args.withAvatar !== false,
      }),
    ),

  generate_character_avatar: (args) =>
    timed('generate_character_avatar', () =>
      api('/workshop/character/avatar', {
        prompt: args.prompt,
        name: args.name || '',
      }),
    ),

  save_character_draft: (args) =>
    timed('save_character_draft', () =>
      api('/workshop/character/save', {
        idea: args.idea,
        worldContext: args.worldContext || '',
        withAvatar: args.withAvatar !== false,
      }),
    ),

  generate_world_tree: (args) =>
    timed('generate_world_tree', () =>
      api('/workshop/world-tree/generate', {
        idea: args.idea,
        language: args.language || '',
        depth: args.depth ?? 3,
      }),
    ),

  expand_world_tree_node: (args) =>
    timed('expand_world_tree_node', () =>
      api('/workshop/world-tree/expand', {
        node: args.node || {},
        hint: args.hint || '',
        language: args.language || '',
      }),
    ),

  rewrite_chat_as_narrative: (args) =>
    timed('rewrite_chat_as_narrative', () =>
      api('/narrative/rewrite', {
        sessionId: args.sessionId,
        formType: args.formType || 'novel',
        style: args.style || 'literary',
        length: args.length || 'medium',
        extraInstruction: args.extraInstruction || '',
      }),
    ),

  list_narrative_sessions: () =>
    timed('list_narrative_sessions', () => api('/narrative/sessions', {}, { method: 'GET' })),

  publish_narrative_work: (args) =>
    timed('publish_narrative_work', () => api(`/narrative/works/${args.workId}/publish`, {})),

  /** 纯本地打包，不调后端，所以不计时也不埋点 */
  export_st_card_json: (args) => ({
    spec: 'chara_card_v2',
    spec_version: '2.0',
    data: {
      name: args.name,
      description: args.personality || args.description || '',
      personality: args.description || '',
      scenario: args.backgroundStory || '',
      first_mes: args.greeting || '',
      mes_example: args.exampleDialogue || '',
      creator_notes: args.description || '',
      tags: args.tags || [],
      avatar: args.avatar || '',
    },
  }),
}

/** 需要把返回值再包一层提示的工具 */
const wrappers = {
  generate_world_tree: (data) => ({
    ...data,
    _hint: '将 data.tree 写入浏览器 localStorage["tavern_world_tree"] 即可在世界树页展示',
  }),
  rewrite_chat_as_narrative: (data) => ({
    ...data,
    _hint: `作品已保存为草稿（id=${data?.id}）。调用 publish_narrative_work 可发布到社区广场。`,
  }),
}

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args = {} } = request.params
  const handler = handlers[name]
  if (!handler) {
    return {
      isError: true,
      content: [{ type: 'text', text: `未知工具: ${name}` }],
    }
  }
  try {
    let data = await handler(args)
    const wrap = wrappers[name]
    if (wrap) {
      data = wrap(data)
    }
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  } catch (e) {
    return {
      isError: true,
      content: [{ type: 'text', text: e.message || String(e) }],
    }
  }
})

const transport = new StdioServerTransport()
await server.connect(transport)
