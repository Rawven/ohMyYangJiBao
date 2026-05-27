export interface ChatResponse {
  conversationId: number | null
  messages: { role: string; content: string }[]
}

export interface RenderBlock {
  type: 'echarts' | 'table' | 'fund-cards'
  option?: any
  columns?: any[]
  dataSource?: any[]
  funds?: any[]
}

export function parseRenderBlocks(text: string): RenderBlock[] {
  const blocks: RenderBlock[] = []
  const echartsRegex = /\[ECHARTS:\s*(\{.+?\})\]/gs
  let match
  while ((match = echartsRegex.exec(text)) !== null) {
    try {
      blocks.push({ type: 'echarts', option: JSON.parse(match[1]) })
    } catch { /* skip */ }
  }
  const tableRegex = /\[TABLE:\s*(\{.+?\})\]/gs
  while ((match = tableRegex.exec(text)) !== null) {
    try {
      const parsed = JSON.parse(match[1])
      blocks.push({ type: 'table', columns: parsed.columns, dataSource: parsed.dataSource })
    } catch { /* skip */ }
  }
  const fundsRegex = /\[FUNDS:\s*(\{.+?\})\]/gs
  while ((match = fundsRegex.exec(text)) !== null) {
    try {
      const parsed = JSON.parse(match[1])
      blocks.push({ type: 'fund-cards', funds: parsed.funds })
    } catch { /* skip */ }
  }
  return blocks
}
