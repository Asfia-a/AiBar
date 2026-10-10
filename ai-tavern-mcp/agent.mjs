#!/usr/bin/env node
/**
 * 自研 MCP 宿主(Agent) —— AI 角色酒馆的智能体客户端
 *
 * 它做两件事:
 *   1. 以 stdio 连接任意 MCP 服务(默认同目录的 server.mjs),把 MCP 的 tool 声明转成 OpenAI 的 tools 格式;
 *   2. 跑一个 function calling 循环:模型决定调用哪个工具 → 经 MCP 执行 → 结果回灌 → 直到模型给出最终回答。
 *
 * 为什么自己写宿主:项目的能力开放层(MCP Server)不应该绑定某一个第三方客户端,
 * 自己实现宿主既能验证协议实现是否正确,也让整条链路可自主演示。
 *
 * 用法:
 *   node agent.mjs --list-tools              只列出工具(不调模型、不需要 Key)
 *   node agent.mjs --selftest                离线自检:连服务 + 列表 + 真调一次纯本地工具
 *   node agent.mjs "帮我设计一个赛博朋克风格的角色,并导出 SillyTavern 卡"
 *   node agent.mjs --max-rounds 6 "先列一下有哪些会话可以改写成小说"
 *
 * 环境变量:
 *   AI_API_KEY      大模型 Key(仅对话模式需要;不设则自动进入 --selftest)
 *   AI_BASE_URL     默认 https://api.deepseek.com/v1
 *   AI_MODEL_NAME   默认 deepseek-chat
 *   TAVERN_API_BASE 透传给 MCP 服务的后端地址
 *   TAVERN_JWT      透传给 MCP 服务的登录态
 *   MCP_SERVER      自定义 MCP 服务脚本路径,默认同目录 server.mjs
 */
import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const SERVER_PATH = process.env.MCP_SERVER || path.join(HERE, 'server.mjs')

const AI_BASE_URL = (process.env.AI_BASE_URL || 'https://api.deepseek.com/v1').replace(/\/$/, '')
const AI_API_KEY = process.env.AI_API_KEY || ''
const AI_MODEL = process.env.AI_MODEL_NAME || 'deepseek-chat'

const argv = process.argv.slice(2)
const flag = (name, def) => {
  const i = argv.indexOf(name)
  return i >= 0 ? argv[i + 1] : def
}
const has = (name) => argv.includes(name)
const goal = argv.filter((a) => !a.startsWith('--') && argv[argv.indexOf(a) - 1] !== '--max-rounds').join(' ').trim()
const MAX_ROUNDS = Number(flag('--max-rounds', 8))

/** 连接 MCP 服务(stdio 子进程) */
async function connectServer() {
  const transport = new StdioClientTransport({
    command: process.execPath, // 用当前 node,避免 PATH 差异
    args: [SERVER_PATH],
    env: {
      ...process.env,
      TAVERN_API_BASE: process.env.TAVERN_API_BASE || 'http://127.0.0.1:8080/api',
      TAVERN_JWT: process.env.TAVERN_JWT || '',
    },
    stderr: 'inherit', // MCP 服务的日志走 stderr,直接透出来
  })
  const client = new Client({ name: 'ai-tavern-agent', version: '1.0.0' }, { capabilities: {} })
  await client.connect(transport)
  return client
}

/** MCP 的 tool 声明 → OpenAI function calling 的 tools 格式(两者几乎 1:1) */
function toOpenAITools(mcpTools) {
  return mcpTools.map((t) => ({
    type: 'function',
    function: {
      name: t.name,
      description: t.description || '',
      parameters: t.inputSchema || { type: 'object', properties: {} },
    },
  }))
}

/** MCP 的 content 数组 → 一段纯文本,方便塞回模型 */
function contentToText(result) {
  const parts = Array.isArray(result?.content) ? result.content : []
  const text = parts
    .map((p) => (p.type === 'text' ? p.text : `[${p.type}]`))
    .join('\n')
  return text || JSON.stringify(result)
}

/** 调一次 OpenAI 兼容的 /chat/completions */
async function chat(messages, tools) {
  const res = await fetch(`${AI_BASE_URL}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${AI_API_KEY}`,
    },
    body: JSON.stringify({
      model: AI_MODEL,
      messages,
      ...(tools.length ? { tools, tool_choice: 'auto' } : {}),
      temperature: 0.7,
    }),
  })
  if (!res.ok) {
    throw new Error(`模型接口 HTTP ${res.status}: ${(await res.text()).slice(0, 300)}`)
  }
  const json = await res.json()
  return json.choices?.[0]?.message ?? {}
}

/** function calling 主循环 */
async function runAgent(client, tools, userGoal) {
  const openaiTools = toOpenAITools(tools)
  const messages = [
    {
      role: 'system',
      content:
        '你是 AI 角色酒馆的创作助手。需要操作平台时调用提供的工具;工具返回的是 JSON,' +
        '你要读懂它并继续下一步,直到能给出最终答复。不要臆造工具返回的数据。',
    },
    { role: 'user', content: userGoal },
  ]

  for (let round = 1; round <= MAX_ROUNDS; round++) {
    console.log(`\n──── 第 ${round} 轮 ────`)
    const msg = await chat(messages, openaiTools)

    if (msg.content) {
      console.log(`模型:${msg.content}`)
    }

    const calls = msg.tool_calls || []
    if (!calls.length) {
      return msg.content || '(模型没有返回内容)'
    }

    // 必须把带 tool_calls 的 assistant 消息原样回灌,否则下一轮模型不知道自己在等结果
    messages.push({ role: 'assistant', content: msg.content || null, tool_calls: calls })

    for (const call of calls) {
      const name = call.function?.name
      let args = {}
      try {
        args = call.function?.arguments ? JSON.parse(call.function.arguments) : {}
      } catch {
        messages.push({
          role: 'tool',
          tool_call_id: call.id,
          content: '参数不是合法 JSON,请重新生成 arguments。',
        })
        console.log(`工具 ${name}:参数解析失败`)
        continue
      }

      console.log(`→ 调用工具 ${name}  ${JSON.stringify(args).slice(0, 120)}`)
      const started = Date.now()
      let result
      try {
        result = await client.callTool({ name, arguments: args })
      } catch (e) {
        result = { isError: true, content: [{ type: 'text', text: e.message || String(e) }] }
      }
      const text = contentToText(result)
      console.log(`← ${result.isError ? '失败' : '成功'} (${Date.now() - started}ms)`)

      messages.push({ role: 'tool', tool_call_id: call.id, content: text })
    }
  }
  return `达到最大轮次(${MAX_ROUNDS}),提前结束。`
}

async function main() {
  const client = await connectServer()
  try {
    const { tools } = await client.listTools()
    console.log(`已连接 MCP 服务,发现 ${tools.length} 个工具:`)
    tools.forEach((t, i) => console.log(`  ${String(i + 1).padStart(2)}. ${t.name}`))

    if (has('--list-tools')) return

    if (has('--selftest') || !AI_API_KEY) {
      if (!has('--selftest') && !AI_API_KEY) {
        console.log('\n未设置 AI_API_KEY,自动进入离线自检。')
      }
      console.log('\n──── 离线自检:真调一次纯本地工具 export_st_card_json ────')
      const r = await client.callTool({
        name: 'export_st_card_json',
        arguments: {
          name: '林晚晴',
          personality: '温柔但嘴硬,喜欢在雨天读诗',
          backgroundStory: '旧书店的店主',
          greeting: '……你又来了。书在第三排,自己拿。',
          tags: ['治愈', '日常'],
        },
      })
      console.log(`结果(${r.isError ? '失败' : '成功'}):`)
      console.log(contentToText(r).slice(0, 400))
      if (r.isError) process.exitCode = 1
      return
    }

    if (!goal) {
      console.log('\n没有给目标。示例:node agent.mjs "帮我生成一个赛博朋克风格的角色卡"')
      return
    }

    console.log(`\n模型:${AI_MODEL} @ ${AI_BASE_URL}`)
    console.log(`目标:${goal}`)
    const answer = await runAgent(client, tools, goal)
    console.log(`\n──── 最终答复 ────\n${answer}`)
  } finally {
    await client.close()
  }
}

main().catch((e) => {
  console.error('agent 启动失败:', e.message || e)
  process.exitCode = 1
})
