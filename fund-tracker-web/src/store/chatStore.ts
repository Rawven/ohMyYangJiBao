import { create } from 'zustand'

export interface RenderBlock {
  type: 'text' | 'echarts' | 'table' | 'fund-cards'
  content?: string
  option?: any
  columns?: any[]
  dataSource?: any[]
  funds?: any[]
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'tool'
  content: string
  blocks?: RenderBlock[]
}

interface ChatState {
  conversationId: number | null
  messages: ChatMessage[]
  loading: boolean
  streaming: boolean
  currentToolCalls: string[]
  abortController: AbortController | null

  setConversationId: (id: number) => void
  addMessage: (msg: ChatMessage) => void
  updateLastAssistant: (text: string, blocks?: RenderBlock[]) => void
  addToolCall: (name: string) => void
  clearToolCalls: () => void
  setLoading: (v: boolean) => void
  setStreaming: (v: boolean) => void
  reset: () => void
  sendMessage: (text: string) => void
  stopStreaming: () => void
  removeLastAssistant: () => ChatMessage | null
}

function parseRenderBlocks(text: string): RenderBlock[] {
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

export const useChatStore = create<ChatState>((set, get) => ({
  conversationId: null,
  messages: [],
  loading: false,
  streaming: false,
  currentToolCalls: [],
  abortController: null,

  setConversationId: (id) => set({ conversationId: id }),
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  updateLastAssistant: (text, blocks) => set((s) => {
    const msgs = [...s.messages]
    const last = msgs[msgs.length - 1]
    if (last && last.role === 'assistant') {
      msgs[msgs.length - 1] = { ...last, content: text, blocks: blocks || last.blocks }
    } else {
      msgs.push({ id: crypto.randomUUID(), role: 'assistant', content: text, blocks })
    }
    return { messages: msgs }
  }),
  addToolCall: (name) => set((s) => ({
    currentToolCalls: [...s.currentToolCalls, name],
  })),
  clearToolCalls: () => set({ currentToolCalls: [] }),
  setLoading: (v) => set({ loading: v }),
  setStreaming: (v) => set({ streaming: v }),
  reset: () => set({ conversationId: null, messages: [], loading: false, streaming: false, currentToolCalls: [], abortController: null }),

  stopStreaming: () => {
    const { abortController } = get()
    if (abortController) {
      abortController.abort()
    }
    set({ streaming: false, currentToolCalls: [], abortController: null })
  },

  removeLastAssistant: () => {
    const msgs = get().messages
    if (msgs.length === 0) return null
    const last = msgs[msgs.length - 1]
    if (last.role !== 'assistant') return null
    set({ messages: msgs.slice(0, -1) })
    return last
  },

  sendMessage: (text) => {
    const { conversationId } = get()
    set({ streaming: true, currentToolCalls: [] })
    const state = get()

    // Add user message
    state.addMessage({ id: crypto.randomUUID(), role: 'user', content: text })
    // Add placeholder assistant message
    state.addMessage({ id: crypto.randomUUID(), role: 'assistant', content: '' })

    const controller = new AbortController()
    set({ abortController: controller })

    fetch('/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ conversationId, message: text }),
      signal: controller.signal,
    }).then(async (response) => {
      const reader = response.body?.getReader()
      if (!reader) throw new Error('No response body')

      // 60 秒超时保护
      const timeoutId = setTimeout(() => {
        controller.abort()
        get().updateLastAssistant('\n\n⏱️ 响应超时，请重试')
        set({ streaming: false, currentToolCalls: [], abortController: null })
      }, 60_000)

      const decoder = new TextDecoder()
      let buffer = ''
      let fullText = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (!line.startsWith('data:')) continue
          const data = line.slice(5).trim()
          if (!data) continue

          try {
            const event = JSON.parse(data)
            switch (event.type) {
              case 'conversation_id':
                set({ conversationId: event.id })
                break
              case 'text':
                fullText += event.content
                get().updateLastAssistant(fullText)
                break
              case 'tool_call':
                get().addToolCall(event.name)
                break
              case 'done':
                clearTimeout(timeoutId)
                get().updateLastAssistant(fullText, parseRenderBlocks(fullText))
                set({ streaming: false, currentToolCalls: [], abortController: null })
                break
              case 'error':
                clearTimeout(timeoutId)
                fullText += `\n\n❌ ${event.content}`
                get().updateLastAssistant(fullText)
                set({ streaming: false, currentToolCalls: [], abortController: null })
                break
            }
          } catch { /* skip parse errors */ }
        }
      }
      clearTimeout(timeoutId)
    }).catch((err) => {
      if (err.name !== 'AbortError') {
        get().updateLastAssistant(`请求失败: ${err.message}`)
        set({ streaming: false, currentToolCalls: [], abortController: null })
      }
    })
  },
}))
