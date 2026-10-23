import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true })

// 模型输出里可能带 HTML 片段。这里直接丢弃原始 HTML，
// 只保留 markdown 结构，避免它把页面结构带歪。
marked.use({
  renderer: {
    html: () => '',
  },
})

/**
 * 把模型返回的文本渲染成 HTML。
 * 只用于 assistant 的消息；用户输入按纯文本显示，不解析。
 */
export function renderMarkdown(text) {
  return marked.parse(String(text ?? ''))
}
